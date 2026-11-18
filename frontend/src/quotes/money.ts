/**
 * Exact decimal preview of the server's quote arithmetic using BigInt (never floating point):
 * lineSubtotal = quantity × unitPrice and lineTax = lineSubtotal × rate / 100, each rounded
 * HALF_UP to the currency's minor unit; totals are sums of the rounded lines.
 * The preview is informational only: saved quotes always show the server's amounts.
 */
const DECIMAL = /^\d+(?:\.\d+)?$/

export function minorDigits(currency: string): number {
  try {
    return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2
  } catch {
    return 2
  }
}

/** Parses a non-negative decimal string to integer units at the given scale; null if not representable. */
export function toUnits(value: string, scale: number): bigint | null {
  if (!DECIMAL.test(value)) return null
  const [whole, fraction = ''] = value.split('.')
  if (fraction.length > scale) return null
  return BigInt(whole + fraction.padEnd(scale, '0'))
}

function roundHalfUp(value: bigint, divisor: bigint) {
  return (value + divisor / 2n) / divisor
}

/** Formats integer units at `scale` as a plain decimal string with exactly two decimals. */
function fromUnits(units: bigint, scale: number): string {
  const cents = scale <= 2 ? units * 10n ** BigInt(2 - scale) : roundHalfUp(units, 10n ** BigInt(scale - 2))
  const text = cents.toString().padStart(3, '0')
  return `${text.slice(0, -2)}.${text.slice(-2)}`
}

export interface LineAmounts { subtotal: string; tax: string; total: string }

export function lineAmounts(quantity: string, unitPrice: string, taxRate: string, currency: string): LineAmounts | null {
  const q = toUnits(quantity, 3)
  const p = toUnits(unitPrice, 2)
  const r = toUnits(taxRate, 2)
  if (q === null || p === null || r === null) return null
  const digits = Math.min(minorDigits(currency), 2)
  const subtotal = roundHalfUp(q * p, 10n ** BigInt(5 - digits))
  const tax = roundHalfUp(subtotal * r, 10_000n)
  return { subtotal: fromUnits(subtotal, digits), tax: fromUnits(tax, digits), total: fromUnits(subtotal + tax, digits) }
}

export interface QuotePreview {
  lines: (LineAmounts | null)[]
  subtotal: string
  taxTotal: string
  grandTotal: string
  taxGroups: { rate: string; base: string; tax: string }[]
  complete: boolean
}

export function previewTotals(items: { quantity: string; unitPrice: string; taxRate: string }[], currency: string): QuotePreview {
  const lines = items.map(item => lineAmounts(item.quantity, item.unitPrice, item.taxRate, currency))
  let subtotal = 0n
  let tax = 0n
  const groups = new Map<string, [bigint, bigint]>()
  lines.forEach((line, index) => {
    if (!line) return
    const base = toUnits(line.subtotal, 2) ?? 0n
    const amount = toUnits(line.tax, 2) ?? 0n
    subtotal += base
    tax += amount
    const rate = fromUnits(toUnits(items[index].taxRate, 2) ?? 0n, 2)
    const current = groups.get(rate) ?? [0n, 0n]
    groups.set(rate, [current[0] + base, current[1] + amount])
  })
  const taxGroups = [...groups.entries()]
    .sort(([a], [b]) => Number((toUnits(a, 2) ?? 0n) - (toUnits(b, 2) ?? 0n)))
    .map(([rate, [base, amount]]) => ({ rate, base: fromUnits(base, 2), tax: fromUnits(amount, 2) }))
  return { lines, subtotal: fromUnits(subtotal, 2), taxTotal: fromUnits(tax, 2), grandTotal: fromUnits(subtotal + tax, 2), taxGroups, complete: lines.every(Boolean) }
}

/** Swiss-style grouping: "CHF 1’234.50"; zero-decimal currencies print without decimals. */
export function formatMoney(amount: string, currency: string): string {
  const [whole, fraction = '00'] = amount.split('.')
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, '’')
  return `${currency} ${minorDigits(currency) === 0 ? grouped : `${grouped}.${fraction.padEnd(2, '0').slice(0, 2)}`}`
}

export function formatRate(rate: string): string {
  return `${rate.replace(/\.?0+$/, '')} %`
}

export function formatQuantity(quantity: string): string {
  const [whole, fraction] = quantity.split('.')
  const trimmed = fraction?.replace(/0+$/, '')
  return whole.replace(/\B(?=(\d{3})+(?!\d))/g, '’') + (trimmed ? `.${trimmed}` : '')
}

export function formatDate(date: string): string {
  const [year, month, day] = date.split('-')
  return `${day}.${month}.${year}`
}
