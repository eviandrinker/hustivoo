import type { Quote, QuoteInput } from '../api/quotes'
import type { CatalogService, ServiceUnit } from '../api/services'
import { minorDigits, toUnits, lineAmounts, previewTotals } from './money'

export interface DraftItem {
  key: string
  serviceId: string | null
  description: string
  quantity: string
  unit: ServiceUnit
  unitPrice: string
  taxRate: string
}
export interface QuoteDraft {
  customerId: string
  issueDate: string
  validUntil: string
  currency: string
  introductionText: string
  closingText: string
  notes: string
  items: DraftItem[]
}

export const MAX_ITEMS = 200
const MAX_UNITS = 9999999999999999999n // 99'999'999'999'999'999.99 in cents, the column limit
const tooLarge = (amount: string) => (toUnits(amount, 2) ?? 0n) > MAX_UNITS
// "10.00" is a whole amount, also for zero-decimal currencies such as JPY.
const trimZeros = (value: string) => value.includes('.') ? value.replace(/0+$/, '').replace(/\.$/, '') : value

let sequence = 0
export function itemKey() { sequence += 1; return `item-${sequence}` }

export function addDays(date: string, days: number): string {
  const value = new Date(`${date}T00:00:00Z`)
  value.setUTCDate(value.getUTCDate() + days)
  return value.toISOString().slice(0, 10)
}

export function localToday(now = new Date()): string {
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

export function emptyDraft(today: string, currency: string): QuoteDraft {
  return { customerId: '', issueDate: today, validUntil: addDays(today, 30), currency, introductionText: '', closingText: '', notes: '', items: [] }
}

export function draftFromQuote(quote: Quote): QuoteDraft {
  return {
    customerId: quote.customerId, issueDate: quote.issueDate, validUntil: quote.validUntil, currency: quote.currency,
    introductionText: quote.introductionText ?? '', closingText: quote.closingText ?? '', notes: quote.notes ?? '',
    items: quote.items.map(item => ({ key: itemKey(), serviceId: item.serviceId, description: item.description, quantity: item.quantity, unit: item.unit, unitPrice: item.unitPrice, taxRate: item.taxRate })),
  }
}

/** Snapshot of a catalogue service as an editable position; later catalogue changes do not affect it. */
export function itemFromService(service: CatalogService): DraftItem {
  const description = service.description ? `${service.name}\n${service.description}` : service.name
  return { key: itemKey(), serviceId: service.id, description: description.slice(0, 4000), quantity: '1', unit: service.unit, unitPrice: service.unitPrice, taxRate: service.taxRate ?? '0.00' }
}

export function freeItem(previous?: DraftItem): DraftItem {
  return { key: itemKey(), serviceId: null, description: '', quantity: '1', unit: 'PIECE', unitPrice: '', taxRate: previous?.taxRate ?? '0.00' }
}

export function moveItem<T>(items: T[], index: number, offset: -1 | 1): T[] {
  const target = index + offset
  if (target < 0 || target >= items.length) return items
  const next = [...items]
  ;[next[index], next[target]] = [next[target], next[index]]
  return next
}

const decimal = (value: string) => value.trim().replace(',', '.')
const text = (value: string) => value.trim() || null

export function normalizeDraft(draft: QuoteDraft): QuoteInput {
  return {
    customerId: draft.customerId, issueDate: draft.issueDate, validUntil: draft.validUntil, currency: draft.currency.trim().toUpperCase(),
    introductionText: text(draft.introductionText), closingText: text(draft.closingText), notes: text(draft.notes),
    items: draft.items.map(item => ({ serviceId: item.serviceId, description: item.description.trim(), quantity: decimal(item.quantity), unit: item.unit, unitPrice: decimal(item.unitPrice), taxRate: decimal(item.taxRate) })),
  }
}

/** Mirrors the server rules; field keys match the server's error fields (e.g. `items[0].unitPrice`). */
export function validateQuote(input: QuoteInput): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!input.customerId) errors.customerId = 'Bitte wähle einen Kunden.'
  if (!/^\d{4}-\d{2}-\d{2}$/.test(input.issueDate)) errors.issueDate = 'Bitte gib ein Ausstellungsdatum ein.'
  if (!/^\d{4}-\d{2}-\d{2}$/.test(input.validUntil)) errors.validUntil = 'Bitte gib ein Gültigkeitsdatum ein.'
  else if (!errors.issueDate && input.validUntil < input.issueDate) errors.validUntil = 'Das Angebot muss mindestens bis zum Ausstellungsdatum gültig sein.'
  const digits = minorDigits(input.currency)
  if (!/^[A-Z]{3}$/.test(input.currency) || digits > 2) errors.currency = 'Bitte wähle eine unterstützte Währung.'
  for (const [key, value] of [['introductionText', input.introductionText], ['closingText', input.closingText], ['notes', input.notes]] as const) {
    if ((value?.length ?? 0) > 4000) errors[key] = 'Bitte verwende höchstens 4000 Zeichen.'
  }
  if (input.items.length > MAX_ITEMS) errors.items = `Ein Angebot kann höchstens ${MAX_ITEMS} Positionen enthalten.`
  input.items.forEach((item, index) => {
    const prefix = `items[${index}].`
    if (!item.description) errors[`${prefix}description`] = 'Bitte beschreibe die Position.'
    else if (item.description.length > 4000) errors[`${prefix}description`] = 'Bitte verwende höchstens 4000 Zeichen.'
    const quantity = toUnits(item.quantity, 3)
    if (quantity === null || quantity === 0n || !/^\d{1,9}(?:\.\d+)?$/.test(item.quantity)) errors[`${prefix}quantity`] = 'Menge grösser als 0, höchstens drei Dezimalstellen.'
    if (toUnits(trimZeros(item.unitPrice), Math.min(digits, 2)) === null || !/^\d{1,17}(?:\.\d+)?$/.test(item.unitPrice)) {
      errors[`${prefix}unitPrice`] = digits === 0 ? 'Bitte gib einen ganzzahligen Preis ein.' : 'Preis ab 0 mit höchstens zwei Dezimalstellen.'
    }
    if (!/^(?:\d{1,2}(?:\.\d{1,2})?|100(?:\.0{1,2})?)$/.test(item.taxRate)) errors[`${prefix}taxRate`] = 'Steuersatz von 0 bis 100 %.'
    const line = lineAmounts(item.quantity, item.unitPrice, item.taxRate, input.currency)
    if (line && tooLarge(line.total)) errors[`${prefix}unitPrice`] = 'Der Positionsbetrag ist zu gross.'
  })
  if (!errors.items && tooLarge(previewTotals(input.items, input.currency).grandTotal)) errors.items = 'Der Gesamtbetrag ist zu gross.'
  return errors
}

export function sendBlocker(quote: Pick<Quote, 'items'>): string | null {
  return quote.items.length ? null : 'Füge mindestens eine Position hinzu, bevor du das Angebot versendest.'
}
