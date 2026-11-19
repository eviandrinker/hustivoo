import { ApiError, requestBlob, requestJson } from './client'
import { serviceUnits, type ServiceUnit } from './services'
import type { QuoteItemInput } from './quotes'

export const invoiceStatuses = ['DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'] as const
export type InvoiceStatus = typeof invoiceStatuses[number]
/** Stored states plus the derived OVERDUE (never stored, see backend README). */
export const displayStatuses = ['DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'CANCELLED'] as const
export type DisplayStatus = typeof displayStatuses[number]
export const paymentMethods = ['BANK_TRANSFER', 'QR_BILL', 'CASH', 'CARD', 'TWINT', 'OTHER'] as const
export type PaymentMethod = typeof paymentMethods[number]

/** Writable fields; totals, due date, status, payments and snapshots are calculated by the server. */
export interface InvoiceInput {
  customerId: string
  issueDate: string
  paymentTermDays: number
  currency: string
  introductionText: string | null
  closingText: string | null
  notes: string | null
  items: QuoteItemInput[]
}
export interface InvoiceItem extends QuoteItemInput {
  id: string
  position: number
  lineSubtotal: string
  lineTax: string
  lineTotal: string
}
export interface InvoicePayment {
  id: string
  paymentDate: string
  amount: string
  method: PaymentMethod
  reference: string | null
  note: string | null
  recordedAt: string
  reversed: boolean
  reversedAt: string | null
  reversalReason: string | null
}
export interface PaymentInput { paymentDate: string; amount: string; method: PaymentMethod; reference: string | null; note: string | null }
export interface InvoiceSummary {
  id: string
  businessId: string
  customerId: string
  invoiceNumber: string
  status: InvoiceStatus
  displayStatus: DisplayStatus
  overdue: boolean
  customerName: string
  issueDate: string
  dueDate: string
  currency: string
  grandTotal: string
  paidTotal: string
  openAmount: string
  updatedAt: string
}
export interface Invoice extends Omit<InvoiceSummary, 'customerName'> {
  paymentTermDays: number
  introductionText: string | null
  closingText: string | null
  notes: string | null
  subtotal: string
  taxTotal: string
  customer: { customerNumber: string; name: string; companyName: string | null; contactName: string | null; street: string | null; postalCode: string | null; city: string | null; country: string; email: string | null }
  items: InvoiceItem[]
  taxGroups: { rate: string; base: string; tax: string }[]
  payments: InvoicePayment[]
  quote: { id: string; quoteNumber: string } | null
  qrBill: { available: boolean; problems: string[] }
  issuedAt: string | null
  cancelledAt: string | null
  cancellationReason: string | null
  createdAt: string
}
export interface InvoiceFilters { search: string; status: DisplayStatus | '' }
export const defaultInvoiceFilters: InvoiceFilters = { search: '', status: '' }

const MONEY = /^\d{1,17}\.\d{2}$/
const QUANTITY = /^\d{1,9}(?:\.\d{1,3})?$/
const RATE = /^(?:\d{1,2}\.\d{2}|100\.00)$/
const DATE = /^\d{4}-\d{2}-\d{2}$/
const NUMBER = /^RE-\d{4,}$/
const isString = (value: unknown): value is string => typeof value === 'string'
const optional = (value: unknown) => value === null || isString(value)
const timestamp = (value: unknown) => isString(value) && Number.isFinite(Date.parse(value))
const money = (value: unknown) => isString(value) && MONEY.test(value)

function record(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new Error('Unexpected invoice response')
  return value as Record<string, unknown>
}
function check(valid: boolean) { if (!valid) throw new Error('Unexpected invoice response') }
function header(data: Record<string, unknown>, businessId: string, id?: string) {
  check(isString(data.id) && Boolean(data.id) && (id === undefined || data.id === id) && data.businessId === businessId &&
    isString(data.customerId) && isString(data.invoiceNumber) && NUMBER.test(data.invoiceNumber) &&
    invoiceStatuses.includes(data.status as InvoiceStatus) && displayStatuses.includes(data.displayStatus as DisplayStatus) &&
    typeof data.overdue === 'boolean' && ['issueDate', 'dueDate'].every(key => isString(data[key]) && DATE.test(data[key])) &&
    isString(data.currency) && /^[A-Z]{3}$/.test(data.currency) && ['grandTotal', 'paidTotal', 'openAmount'].every(key => money(data[key])) &&
    timestamp(data.updatedAt))
}
function parseSummary(value: unknown, businessId: string): InvoiceSummary {
  const data = record(value)
  header(data, businessId)
  check(isString(data.customerName))
  return data as unknown as InvoiceSummary
}
function parsePayment(value: unknown): InvoicePayment {
  const data = record(value)
  check(isString(data.id) && isString(data.paymentDate) && DATE.test(data.paymentDate) && money(data.amount) &&
    paymentMethods.includes(data.method as PaymentMethod) && optional(data.reference) && optional(data.note) &&
    timestamp(data.recordedAt) && typeof data.reversed === 'boolean' && optional(data.reversedAt) && optional(data.reversalReason))
  return data as unknown as InvoicePayment
}
export function parseInvoice(value: unknown, businessId: string, id?: string): Invoice {
  const data = record(value)
  header(data, businessId, id)
  check(Number.isInteger(data.paymentTermDays) && ['subtotal', 'taxTotal'].every(key => money(data[key])) &&
    ['introductionText', 'closingText', 'notes', 'issuedAt', 'cancelledAt', 'cancellationReason'].every(key => optional(data[key])) &&
    timestamp(data.createdAt) && Array.isArray(data.items) && Array.isArray(data.taxGroups) && Array.isArray(data.payments))
  const customer = record(data.customer)
  check(isString(customer.customerNumber) && isString(customer.name) && Boolean(customer.name) && isString(customer.country) &&
    ['companyName', 'contactName', 'street', 'postalCode', 'city', 'email'].every(key => optional(customer[key])))
  const items = (data.items as unknown[]).map(item => {
    const entry = record(item)
    check(isString(entry.id) && optional(entry.serviceId) && Number.isInteger(entry.position) && isString(entry.description) &&
      isString(entry.quantity) && QUANTITY.test(entry.quantity) && serviceUnits.includes(entry.unit as ServiceUnit) &&
      isString(entry.taxRate) && RATE.test(entry.taxRate) && ['unitPrice', 'lineSubtotal', 'lineTax', 'lineTotal'].every(key => money(entry[key])))
    return entry as unknown as InvoiceItem
  })
  const taxGroups = (data.taxGroups as unknown[]).map(group => {
    const entry = record(group)
    check(isString(entry.rate) && RATE.test(entry.rate) && money(entry.base) && money(entry.tax))
    return entry as unknown as Invoice['taxGroups'][number]
  })
  const quote = data.quote === null ? null : record(data.quote)
  check(quote === null || (isString(quote.id) && isString(quote.quoteNumber)))
  const qr = record(data.qrBill)
  check(typeof qr.available === 'boolean' && Array.isArray(qr.problems) && qr.problems.every(isString))
  return {
    ...(data as unknown as Invoice), customer: customer as unknown as Invoice['customer'], items, taxGroups,
    payments: (data.payments as unknown[]).map(parsePayment), quote: quote as Invoice['quote'],
    qrBill: { available: qr.available as boolean, problems: qr.problems as string[] },
  }
}

function base(businessId: string) { return `/api/businesses/${encodeURIComponent(businessId)}/invoices` }
function path(businessId: string, id: string) { return `${base(businessId)}/${encodeURIComponent(id)}` }
const json = (method: string, body: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })

export async function listInvoices(businessId: string, filters: InvoiceFilters = defaultInvoiceFilters) {
  const params = new URLSearchParams({ search: filters.search })
  if (filters.status) params.set('status', filters.status)
  const result = await requestJson(`${base(businessId)}?${params}`)
  if (!Array.isArray(result)) throw new Error('Unexpected invoice list')
  return result.map(value => parseSummary(value, businessId))
}
export async function getInvoice(businessId: string, id: string) {
  return parseInvoice(await requestJson(path(businessId, id)), businessId, id)
}
export async function saveInvoice(businessId: string, input: InvoiceInput, id?: string) {
  // Whitelist writable fields: totals, numbers, status, payments and snapshots are never sent.
  const { customerId, issueDate, paymentTermDays, currency, introductionText, closingText, notes } = input
  const items = input.items.map(({ serviceId, description, quantity, unit, unitPrice, taxRate }) => ({ serviceId, description, quantity, unit, unitPrice, taxRate }))
  return parseInvoice(await requestJson(id ? path(businessId, id) : base(businessId),
    json(id ? 'PUT' : 'POST', { customerId, issueDate, paymentTermDays, currency, introductionText, closingText, notes, items })), businessId, id)
}
export async function deleteInvoice(businessId: string, id: string) {
  await requestJson(path(businessId, id), { method: 'DELETE' })
}
export async function issueInvoice(businessId: string, id: string) {
  return parseInvoice(await requestJson(`${path(businessId, id)}/issue`, { method: 'PATCH' }), businessId, id)
}
export async function cancelInvoice(businessId: string, id: string, reason: string) {
  return parseInvoice(await requestJson(`${path(businessId, id)}/cancel`, json('PATCH', { reason })), businessId, id)
}
export async function recordPayment(businessId: string, id: string, payment: PaymentInput) {
  const { paymentDate, amount, method, reference, note } = payment
  return parseInvoice(await requestJson(`${path(businessId, id)}/payments`, json('POST', { paymentDate, amount, method, reference, note })), businessId, id)
}
export async function reversePayment(businessId: string, id: string, paymentId: string, reason: string) {
  return parseInvoice(await requestJson(`${path(businessId, id)}/payments/${encodeURIComponent(paymentId)}/reverse`, json('PATCH', { reason })), businessId, id)
}
export async function downloadInvoicePdf(businessId: string, id: string, signal?: AbortSignal) {
  return requestBlob(`${path(businessId, id)}/pdf`, 'application/pdf', signal)
}

/**
 * Converts an accepted quote. A repeated conversion (double click, second tab) resolves to the invoice
 * that already exists, so callers can simply navigate to the returned ID.
 */
export async function invoiceFromQuote(businessId: string, quoteId: string): Promise<{ id: string; created: boolean }> {
  try {
    const invoice = parseInvoice(await requestJson(`/api/businesses/${encodeURIComponent(businessId)}/quotes/${encodeURIComponent(quoteId)}/invoice`, { method: 'POST' }), businessId)
    return { id: invoice.id, created: true }
  } catch (error) {
    if (error instanceof ApiError && error.status === 409 && error.invoiceId) return { id: error.invoiceId, created: false }
    throw error
  }
}
