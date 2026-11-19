import type { ExpenseInput, SupplierInput } from '../api/expenses'
export const statusLabels = { UNPAID: 'Offen', PAID: 'Bezahlt', CANCELLED: 'Storniert', OVERDUE: 'Überfällig' }
export const inputTaxLabels = { UNCHECKED: 'Ungeprüft / unklar', DEDUCTIBLE: 'Als abzugsfähig geprüft', NOT_DEDUCTIBLE: 'Nicht abzugsfähig', PARTIAL: 'Teilweise abzugsfähig (separat prüfen)' }
export function validateExpense(input: ExpenseInput) {
  const errors: Record<string, string> = {}
  if (!input.description.trim()) errors.description = 'Bitte beschreibe die Ausgabe.'
  else if (input.description.length > 1000) errors.description = 'Maximal 1000 Zeichen.'
  if (!input.supplierId) errors.supplierId = 'Bitte wähle einen Lieferanten.'
  if (!input.categoryId) errors.categoryId = 'Bitte wähle eine Kategorie.'
  if (!/^\d{4}-\d{2}-\d{2}$/.test(input.expenseDate)) errors.expenseDate = 'Bitte wähle ein Datum.'
  if (input.dueDate && input.dueDate < input.expenseDate) errors.dueDate = 'Fälligkeit darf nicht vor dem Ausgabedatum liegen.'
  if (!/^\d{1,17}(\.\d{1,2})?$/.test(input.netAmount)) errors.netAmount = 'Nicht negativer Betrag, maximal zwei Dezimalstellen.'
  if (input.taxRate !== null && !/^(\d{1,2}(\.\d{1,2})?|100(\.0{1,2})?)$/.test(input.taxRate)) errors.taxRate = 'Steuersatz zwischen 0 und 100, maximal zwei Dezimalstellen.'
  if (input.taxAmount !== null && !/^\d{1,17}(\.\d{1,2})?$/.test(input.taxAmount)) errors.taxAmount = 'Bitte prüfe den ausgewiesenen Steuerbetrag.'
  if (!/^[A-Z]{3}$/.test(input.currency)) errors.currency = 'Bitte prüfe die Währung.'
  if ((input.reference?.length ?? 0) > 140) errors.reference = 'Maximal 140 Zeichen.'
  if ((input.notes?.length ?? 0) > 4000) errors.notes = 'Maximal 4000 Zeichen.'
  return errors
}
export function validateSupplier(input: SupplierInput) {
  const errors: Record<string, string> = {}
  if (!input.name.trim() || input.name.length > 255) errors.name = 'Bitte gib einen Namen mit maximal 255 Zeichen ein.'
  if (input.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email)) errors.email = 'Bitte prüfe die E-Mail-Adresse.'
  return errors
}
const cents = (value: string) => { const [integer, fraction = ''] = value.split('.'); return BigInt(integer) * 100n + BigInt(fraction.padEnd(2, '0')) }
const decimal = (value: bigint) => `${value / 100n}.${(value % 100n).toString().padStart(2, '0')}`
/** Exact, provisional display only. The backend independently validates/recomputes all submitted amounts. */
export function expensePreview(net: string, rate: string | null, override: string | null) {
  if (!/^\d{1,17}(\.\d{1,2})?$/.test(net) || (rate !== null && !/^\d{1,3}(\.\d{1,2})?$/.test(rate)) || (override !== null && !/^\d{1,17}(\.\d{1,2})?$/.test(override))) return null
  const tax = override !== null ? cents(override) : rate === null ? 0n : (cents(net) * cents(rate) + 5000n) / 10000n
  return { tax: decimal(tax), gross: decimal(cents(net) + tax) }
}
export function validateReceipt(file: { size: number; type: string }) { return file.size > 10 * 1024 * 1024 || file.size === 0 ? 'Maximal 10 MB pro Datei.' : !['application/pdf', 'image/png', 'image/jpeg'].includes(file.type) ? 'Bitte wähle PDF, JPEG oder PNG.' : null }
