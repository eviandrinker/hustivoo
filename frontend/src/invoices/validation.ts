import type { Invoice, InvoiceInput, PaymentInput, PaymentMethod } from '../api/invoices'
import { toUnits, minorDigits } from '../quotes/money'
import { addDays, itemKey, validatePositions, type DraftItem } from '../quotes/validation'

export interface InvoiceDraft {
  customerId: string
  issueDate: string
  paymentTermDays: string
  currency: string
  introductionText: string
  closingText: string
  notes: string
  items: DraftItem[]
}
export const DEFAULT_TERM = 30
export const paymentTerms = [0, 10, 14, 20, 30, 45, 60, 90]

export function emptyInvoiceDraft(today: string, currency: string): InvoiceDraft {
  return { customerId: '', issueDate: today, paymentTermDays: String(DEFAULT_TERM), currency, introductionText: '', closingText: '', notes: '', items: [] }
}

export function draftFromInvoice(invoice: Invoice): InvoiceDraft {
  return {
    customerId: invoice.customerId, issueDate: invoice.issueDate, paymentTermDays: String(invoice.paymentTermDays), currency: invoice.currency,
    introductionText: invoice.introductionText ?? '', closingText: invoice.closingText ?? '', notes: invoice.notes ?? '',
    items: invoice.items.map(item => ({ key: itemKey(), serviceId: item.serviceId, description: item.description, quantity: item.quantity, unit: item.unit, unitPrice: item.unitPrice, taxRate: item.taxRate })),
  }
}

const decimal = (value: string) => value.trim().replace(',', '.')
const text = (value: string) => value.trim() || null

export function normalizeInvoiceDraft(draft: InvoiceDraft): InvoiceInput {
  return {
    customerId: draft.customerId, issueDate: draft.issueDate, paymentTermDays: /^\d+$/.test(draft.paymentTermDays.trim()) ? Number(draft.paymentTermDays.trim()) : -1,
    currency: draft.currency.trim().toUpperCase(), introductionText: text(draft.introductionText), closingText: text(draft.closingText), notes: text(draft.notes),
    items: draft.items.map(item => ({ serviceId: item.serviceId, description: item.description.trim(), quantity: decimal(item.quantity), unit: item.unit, unitPrice: decimal(item.unitPrice), taxRate: decimal(item.taxRate) })),
  }
}

/** The due date is always derived by the server the same way: issue date + payment term. */
export function dueDate(input: Pick<InvoiceInput, 'issueDate' | 'paymentTermDays'>): string | null {
  return /^\d{4}-\d{2}-\d{2}$/.test(input.issueDate) && input.paymentTermDays >= 0 ? addDays(input.issueDate, input.paymentTermDays) : null
}

/** Mirrors the server rules; field keys match the server's error fields. */
export function validateInvoice(input: InvoiceInput): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!input.customerId) errors.customerId = 'Bitte wähle einen Kunden.'
  if (!/^\d{4}-\d{2}-\d{2}$/.test(input.issueDate)) errors.issueDate = 'Bitte gib ein Rechnungsdatum ein.'
  if (!Number.isInteger(input.paymentTermDays) || input.paymentTermDays < 0 || input.paymentTermDays > 365) errors.paymentTermDays = 'Zahlungsfrist von 0 bis 365 Tagen.'
  return { ...errors, ...validatePositions(input, 'Eine Rechnung') }
}

export interface PaymentDraft { paymentDate: string; amount: string; method: PaymentMethod; reference: string; note: string }

export function emptyPayment(today: string, openAmount: string): PaymentDraft {
  return { paymentDate: today, amount: openAmount, method: 'BANK_TRANSFER', reference: '', note: '' }
}

export function normalizePayment(draft: PaymentDraft): PaymentInput {
  return { paymentDate: draft.paymentDate, amount: decimal(draft.amount), method: draft.method, reference: text(draft.reference), note: text(draft.note) }
}

/** Positive amount with at most the currency's minor digits, never above the open amount, not in the future. */
export function validatePayment(input: PaymentInput, openAmount: string, currency: string, today: string): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!/^\d{4}-\d{2}-\d{2}$/.test(input.paymentDate)) errors.paymentDate = 'Bitte gib das Zahlungsdatum ein.'
  else if (input.paymentDate > today) errors.paymentDate = 'Das Zahlungsdatum darf nicht in der Zukunft liegen.'
  const digits = Math.min(minorDigits(currency), 2)
  const amount = /^\d{1,17}(?:\.\d+)?$/.test(input.amount) ? toUnits(input.amount.includes('.') ? input.amount.replace(/0+$/, '').replace(/\.$/, '') : input.amount, digits) : null
  const open = toUnits(openAmount, 2) ?? 0n
  const scaled = amount === null ? null : amount * 10n ** BigInt(2 - digits)
  if (scaled === null || scaled <= 0n) errors.amount = digits === 0 ? 'Bitte gib einen ganzzahligen Betrag über 0 ein.' : 'Betrag über 0 mit höchstens zwei Dezimalstellen.'
  else if (scaled > open) errors.amount = 'Der Betrag ist höher als der offene Betrag.'
  if ((input.reference?.length ?? 0) > 140) errors.reference = 'Höchstens 140 Zeichen.'
  if ((input.note?.length ?? 0) > 1000) errors.note = 'Höchstens 1000 Zeichen.'
  return errors
}

export function validateReason(reason: string): string | null {
  const value = reason.trim()
  if (!value) return 'Bitte gib einen Grund an. Er bleibt nachvollziehbar gespeichert.'
  if (value.length > 500) return 'Höchstens 500 Zeichen.'
  return null
}
