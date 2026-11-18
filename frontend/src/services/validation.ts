import type { ServiceInput, ServiceUnit } from '../api/services'
export const unitLabels: Record<ServiceUnit, string> = { HOUR: 'Stunde', DAY: 'Tag', FIXED: 'Pauschal', PIECE: 'Stück', MONTH: 'Monat' }
export function normalizeService(input: ServiceInput): ServiceInput {
  return {
    name: input.name.trim(), description: input.description?.trim() || null, category: input.category?.trim() || null,
    unit: input.unit, unitPrice: input.unitPrice.trim().replace(',', '.'), currency: input.currency.trim().toUpperCase(),
    taxRate: input.taxRate?.trim().replace(',', '.') || null,
  }
}
export function validateService(input: ServiceInput): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!input.name.trim()) errors.name = 'Bitte gib einen Namen ein.'
  else if (input.name.length > 255) errors.name = 'Bitte verwende höchstens 255 Zeichen.'
  if ((input.description?.length ?? 0) > 4000) errors.description = 'Bitte verwende höchstens 4000 Zeichen.'
  if ((input.category?.length ?? 0) > 120) errors.category = 'Bitte verwende höchstens 120 Zeichen.'
  if (!Object.hasOwn(unitLabels, input.unit)) errors.unit = 'Bitte wähle eine Einheit.'
  if (!/^\d{1,17}(?:\.\d{1,2})?$/.test(input.unitPrice)) errors.unitPrice = 'Gib einen positiven Preis oder 0 mit höchstens zwei Dezimalstellen ein (max. 17 Vorkommastellen).'
  if (!/^[A-Z]{3}$/.test(input.currency)) errors.currency = 'Bitte gib eine gültige Währung ein, beispielsweise CHF.'
  if (input.taxRate !== null && !/^(?:\d{1,2}(?:\.\d{1,2})?|100(?:\.0{1,2})?)$/.test(input.taxRate)) errors.taxRate = 'Gib einen Steuersatz von 0 bis 100 mit höchstens zwei Dezimalstellen ein.'
  return errors
}
export function formatPrice(price: string, currency: string) {
  const [whole, fraction = ''] = price.split('.')
  return `${currency} ${whole.replace(/\B(?=(\d{3})+(?!\d))/g, '’')}.${fraction.padEnd(2, '0')}`
}
