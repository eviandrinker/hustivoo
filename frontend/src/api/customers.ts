import { requestJson } from './client'

export interface CustomerInput {
  type: 'PRIVATE' | 'COMPANY'
  displayName: string
  companyName: string | null
  firstName: string | null
  lastName: string | null
  email: string | null
  phone: string | null
  street: string | null
  postalCode: string | null
  city: string | null
  country: string
  notes: string | null
}
export type CustomerStatus = 'LEAD' | 'ACTIVE' | 'INACTIVE'
export interface CustomerFilters { status?: CustomerStatus | ''; type?: CustomerInput['type'] | ''; sort?: 'NAME_ASC' | 'NAME_DESC' | 'CREATED_ASC' | 'CREATED_DESC' }
export interface Customer extends CustomerInput {
  id: string
  businessId: string
  customerNumber: string
  status: CustomerStatus
  archived: boolean
  createdAt: string
  updatedAt: string
}
function parseCustomer(value: unknown, businessId: string): Customer {
  if (typeof value !== 'object' || value === null) throw new Error('Unexpected customer')
  const data = value as Record<string, unknown>
  if (typeof data.id !== 'string' || !data.id || data.businessId !== businessId ||
      !['PRIVATE', 'COMPANY'].includes(String(data.type)) || typeof data.displayName !== 'string' || !data.displayName ||
      typeof data.customerNumber !== 'string' || !/^K-\d+$/.test(data.customerNumber) || !['LEAD', 'ACTIVE', 'INACTIVE'].includes(String(data.status)) ||
      typeof data.archived !== 'boolean' || typeof data.country !== 'string' || !/^[A-Z]{2}$/.test(data.country) ||
      !['createdAt', 'updatedAt'].every(key => typeof data[key] === 'string' && Number.isFinite(Date.parse(data[key]))) ||
      !['companyName', 'firstName', 'lastName', 'email', 'phone', 'street', 'postalCode', 'city', 'notes'].every(key => data[key] === null || typeof data[key] === 'string')) throw new Error('Unexpected customer')
  return data as unknown as Customer
}
function base(businessId: string) { return `/api/businesses/${encodeURIComponent(businessId)}/customers` }
export async function listCustomers(businessId: string, search = '', includeArchived = false, filters: CustomerFilters = {}) {
  const params = new URLSearchParams({ search, includeArchived: String(includeArchived) })
  for (const [key, value] of Object.entries(filters)) if (value) params.set(key, value)
  const result = await requestJson(`${base(businessId)}?${params}`)
  if (!Array.isArray(result)) throw new Error('Unexpected customers')
  return result.map(value => parseCustomer(value, businessId))
}
export async function getCustomer(businessId: string, id: string) {
  return parseCustomer(await requestJson(`${base(businessId)}/${encodeURIComponent(id)}`), businessId)
}
export async function saveCustomer(businessId: string, input: CustomerInput, id?: string) {
  return parseCustomer(await requestJson(base(businessId) + (id ? `/${encodeURIComponent(id)}` : ''), {
    method: id ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
  }), businessId)
}
export async function archiveCustomer(businessId: string, id: string, archived: boolean) {
  return parseCustomer(await requestJson(`${base(businessId)}/${encodeURIComponent(id)}/${archived ? 'archive' : 'restore'}`, { method: 'PATCH' }), businessId)
}

export interface ContactInput { firstName: string; lastName: string; jobTitle: string | null; email: string | null; phone: string | null; primaryContact: boolean }
export interface Contact extends ContactInput { id: string; businessId: string; customerId: string }
export interface CustomerNote { id: string; businessId: string; customerId: string; content: string; createdBy: string | null; authorName: string; createdAt: string }
export const activityEvents = ['CREATED', 'UPDATED', 'STATUS_CHANGED', 'CONTACT_CREATED', 'CONTACT_UPDATED', 'CONTACT_REMOVED', 'NOTE_ADDED', 'ARCHIVED', 'RESTORED'] as const
export interface CustomerActivity extends Omit<CustomerNote, 'content'> { event: typeof activityEvents[number] }
function childPath(businessId: string, customerId: string) { return `${base(businessId)}/${encodeURIComponent(customerId)}` }
function scoped(value: unknown, businessId: string, customerId: string): Record<string, unknown> {
  if (typeof value !== 'object' || value === null) throw new Error('Unexpected CRM response')
  const data = value as Record<string, unknown>
  if (typeof data.id !== 'string' || !data.id || data.businessId !== businessId || data.customerId !== customerId) throw new Error('Unexpected CRM scope')
  return data
}
function contact(value: unknown, businessId: string, customerId: string): Contact {
  const data = scoped(value, businessId, customerId)
  if (!['firstName', 'lastName'].every(key => typeof data[key] === 'string' && data[key]) ||
      !['jobTitle', 'email', 'phone'].every(key => data[key] === null || typeof data[key] === 'string') || typeof data.primaryContact !== 'boolean') throw new Error('Unexpected contact')
  return data as unknown as Contact
}
function historyEntry(value: unknown, businessId: string, customerId: string) {
  const data = scoped(value, businessId, customerId)
  if (!(data.createdBy === null || typeof data.createdBy === 'string') || typeof data.authorName !== 'string' ||
      typeof data.createdAt !== 'string' || !Number.isFinite(Date.parse(data.createdAt))) throw new Error('Unexpected history')
  return data
}
function note(value: unknown, businessId: string, customerId: string): CustomerNote {
  const data = historyEntry(value, businessId, customerId)
  if (typeof data.content !== 'string') throw new Error('Unexpected note')
  return data as unknown as CustomerNote
}
async function listChildren<T>(businessId: string, customerId: string, resource: string, parse: (value: unknown, business: string, customer: string) => T) {
  const data = await requestJson(`${childPath(businessId, customerId)}/${resource}`)
  if (!Array.isArray(data)) throw new Error('Unexpected CRM list')
  return data.map(item => parse(item, businessId, customerId))
}
export function listContacts(businessId: string, customerId: string) { return listChildren(businessId, customerId, 'contacts', contact) }
export function listNotes(businessId: string, customerId: string) { return listChildren(businessId, customerId, 'notes', note) }
export function listActivities(businessId: string, customerId: string) {
  return listChildren(businessId, customerId, 'activities', (value, business, customer) => {
    const data = historyEntry(value, business, customer)
    if (!activityEvents.includes(data.event as typeof activityEvents[number])) throw new Error('Unexpected activity')
    return data as unknown as CustomerActivity
  })
}
function jsonRequest(method: string, body: unknown): RequestInit { return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } }
export async function updateCustomerStatus(businessId: string, customerId: string, status: CustomerStatus) {
  return parseCustomer(await requestJson(`${childPath(businessId, customerId)}/status`, jsonRequest('PATCH', { status })), businessId)
}
export async function saveContact(businessId: string, customerId: string, input: ContactInput, id?: string) {
  return contact(await requestJson(`${childPath(businessId, customerId)}/contacts${id ? '/' + encodeURIComponent(id) : ''}`, jsonRequest(id ? 'PUT' : 'POST', input)), businessId, customerId)
}
export async function removeContact(businessId: string, customerId: string, id: string) {
  await requestJson(`${childPath(businessId, customerId)}/contacts/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
export async function addNote(businessId: string, customerId: string, content: string) {
  return note(await requestJson(`${childPath(businessId, customerId)}/notes`, jsonRequest('POST', { content })), businessId, customerId)
}
