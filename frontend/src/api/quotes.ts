import { requestBlob, requestJson } from './client'
import { serviceUnits, type ServiceUnit } from './services'

export const quoteStatuses = ['DRAFT', 'SENT', 'ACCEPTED', 'REJECTED'] as const
export type QuoteStatus = typeof quoteStatuses[number]
export type QuoteAction = 'send' | 'accept' | 'reject'

/** Writable position fields. Amounts stay decimal strings; the server computes every total. */
export interface QuoteItemInput {
  serviceId: string | null
  description: string
  quantity: string
  unit: ServiceUnit
  unitPrice: string
  taxRate: string
}
export interface QuoteInput {
  customerId: string
  issueDate: string
  validUntil: string
  currency: string
  introductionText: string | null
  closingText: string | null
  notes: string | null
  items: QuoteItemInput[]
}
export interface QuoteItem extends QuoteItemInput {
  id: string
  position: number
  lineSubtotal: string
  lineTax: string
  lineTotal: string
}
export interface QuoteCustomer {
  customerNumber: string
  name: string
  companyName: string | null
  contactName: string | null
  street: string | null
  postalCode: string | null
  city: string | null
  country: string
  email: string | null
}
export interface QuoteTaxGroup { rate: string; base: string; tax: string }
export interface Quote {
  id: string
  businessId: string
  customerId: string
  quoteNumber: string
  status: QuoteStatus
  issueDate: string
  validUntil: string
  currency: string
  introductionText: string | null
  closingText: string | null
  notes: string | null
  subtotal: string
  taxTotal: string
  grandTotal: string
  customer: QuoteCustomer
  items: QuoteItem[]
  taxGroups: QuoteTaxGroup[]
  sentAt: string | null
  decidedAt: string | null
  createdAt: string
  updatedAt: string
}
export interface QuoteSummary {
  id: string
  businessId: string
  customerId: string
  quoteNumber: string
  status: QuoteStatus
  customerName: string
  issueDate: string
  validUntil: string
  currency: string
  grandTotal: string
  updatedAt: string
}
export interface QuoteFilters { search: string; status: QuoteStatus | '' }
export const defaultQuoteFilters: QuoteFilters = { search: '', status: '' }

const MONEY = /^\d{1,17}\.\d{2}$/
const QUANTITY = /^\d{1,9}(?:\.\d{1,3})?$/
const RATE = /^(?:\d{1,2}\.\d{2}|100\.00)$/
const DATE = /^\d{4}-\d{2}-\d{2}$/
const NUMBER = /^ANG-\d{4,}$/
const isString = (value: unknown): value is string => typeof value === 'string'
const optional = (value: unknown) => value === null || isString(value)
const timestamp = (value: unknown) => isString(value) && Number.isFinite(Date.parse(value))

function record(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new Error('Unexpected quote response')
  return value as Record<string, unknown>
}
function check(valid: boolean) { if (!valid) throw new Error('Unexpected quote response') }
function header(data: Record<string, unknown>, businessId: string, id?: string) {
  check(isString(data.id) && Boolean(data.id) && (id === undefined || data.id === id) && data.businessId === businessId &&
    isString(data.customerId) && isString(data.quoteNumber) && NUMBER.test(data.quoteNumber) &&
    quoteStatuses.includes(data.status as QuoteStatus) && ['issueDate', 'validUntil'].every(key => isString(data[key]) && DATE.test(data[key])) &&
    isString(data.currency) && /^[A-Z]{3}$/.test(data.currency) && timestamp(data.updatedAt))
}
function parseSummary(value: unknown, businessId: string): QuoteSummary {
  const data = record(value)
  header(data, businessId)
  check(isString(data.customerName) && isString(data.grandTotal) && MONEY.test(data.grandTotal))
  return data as unknown as QuoteSummary
}
function parseItem(value: unknown): QuoteItem {
  const data = record(value)
  check(isString(data.id) && optional(data.serviceId) && Number.isInteger(data.position) && (data.position as number) > 0 &&
    isString(data.description) && Boolean(data.description) && isString(data.quantity) && QUANTITY.test(data.quantity) &&
    serviceUnits.includes(data.unit as ServiceUnit) && isString(data.taxRate) && RATE.test(data.taxRate) &&
    ['unitPrice', 'lineSubtotal', 'lineTax', 'lineTotal'].every(key => isString(data[key]) && MONEY.test(data[key])))
  return data as unknown as QuoteItem
}
export function parseQuote(value: unknown, businessId: string, id?: string): Quote {
  const data = record(value)
  header(data, businessId, id)
  check(['subtotal', 'taxTotal', 'grandTotal'].every(key => isString(data[key]) && MONEY.test(data[key])) &&
    ['introductionText', 'closingText', 'notes'].every(key => optional(data[key])) &&
    optional(data.sentAt) && optional(data.decidedAt) && timestamp(data.createdAt) &&
    Array.isArray(data.items) && Array.isArray(data.taxGroups))
  const customer = record(data.customer)
  check(isString(customer.customerNumber) && isString(customer.name) && Boolean(customer.name) && isString(customer.country) &&
    ['companyName', 'contactName', 'street', 'postalCode', 'city', 'email'].every(key => optional(customer[key])))
  const items = (data.items as unknown[]).map(parseItem)
  const taxGroups = (data.taxGroups as unknown[]).map(group => {
    const entry = record(group)
    check(isString(entry.rate) && RATE.test(entry.rate) && ['base', 'tax'].every(key => isString(entry[key]) && MONEY.test(entry[key])))
    return entry as unknown as QuoteTaxGroup
  })
  return { ...(data as unknown as Quote), customer: customer as unknown as QuoteCustomer, items, taxGroups }
}

function base(businessId: string) { return `/api/businesses/${encodeURIComponent(businessId)}/quotes` }
function path(businessId: string, id: string) { return `${base(businessId)}/${encodeURIComponent(id)}` }

export async function listQuotes(businessId: string, filters: QuoteFilters = defaultQuoteFilters) {
  const params = new URLSearchParams({ search: filters.search })
  if (filters.status) params.set('status', filters.status)
  const result = await requestJson(`${base(businessId)}?${params}`)
  if (!Array.isArray(result)) throw new Error('Unexpected quote list')
  return result.map(value => parseSummary(value, businessId))
}
export async function getQuote(businessId: string, id: string) {
  return parseQuote(await requestJson(path(businessId, id)), businessId, id)
}
export async function saveQuote(businessId: string, input: QuoteInput, id?: string) {
  // Whitelist writable fields: totals, numbers, status and snapshots are never sent.
  const { customerId, issueDate, validUntil, currency, introductionText, closingText, notes } = input
  const items = input.items.map(({ serviceId, description, quantity, unit, unitPrice, taxRate }) => ({ serviceId, description, quantity, unit, unitPrice, taxRate }))
  return parseQuote(await requestJson(id ? path(businessId, id) : base(businessId), {
    method: id ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ customerId, issueDate, validUntil, currency, introductionText, closingText, notes, items }),
  }), businessId, id)
}
export async function deleteQuote(businessId: string, id: string) {
  await requestJson(path(businessId, id), { method: 'DELETE' })
}
export async function changeQuoteStatus(businessId: string, id: string, action: QuoteAction) {
  return parseQuote(await requestJson(`${path(businessId, id)}/${action}`, { method: 'PATCH' }), businessId, id)
}
export async function downloadQuotePdf(businessId: string, id: string, signal?: AbortSignal) {
  return requestBlob(`${path(businessId, id)}/pdf`, 'application/pdf', signal)
}
