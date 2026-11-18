import { requestJson } from './client'

export const serviceUnits = ['HOUR', 'DAY', 'FIXED', 'PIECE', 'MONTH'] as const
export type ServiceUnit = typeof serviceUnits[number]
/** Monetary values stay decimal strings throughout the frontend. */
export interface ServiceInput {
  name: string
  description: string | null
  category: string | null
  unit: ServiceUnit
  unitPrice: string
  currency: string
  taxRate: string | null
}
export interface CatalogService extends ServiceInput {
  id: string
  businessId: string
  active: boolean
  createdAt: string
  updatedAt: string
}
export interface ServiceFilters {
  search: string
  category: string
  active: boolean
  sort: 'NAME_ASC' | 'NAME_DESC' | 'PRICE_ASC' | 'PRICE_DESC'
}
export const defaultServiceFilters: ServiceFilters = { search: '', category: '', active: true, sort: 'NAME_ASC' }
function parseService(value: unknown, businessId: string, id?: string): CatalogService {
  if (typeof value !== 'object' || value === null) throw new Error('Unexpected service response')
  const data = value as Record<string, unknown>
  if (typeof data.id !== 'string' || !data.id || (id !== undefined && data.id !== id) || data.businessId !== businessId ||
      typeof data.name !== 'string' || !data.name.trim() || typeof data.active !== 'boolean' ||
      !serviceUnits.includes(data.unit as ServiceUnit) || typeof data.currency !== 'string' || !/^[A-Z]{3}$/.test(data.currency) ||
      typeof data.unitPrice !== 'string' || !/^\d{1,17}\.\d{2}$/.test(data.unitPrice) ||
      !(data.taxRate === null || typeof data.taxRate === 'string' && /^(?:\d{1,2}\.\d{2}|100\.00)$/.test(data.taxRate)) ||
      !['description', 'category'].every(key => data[key] === null || typeof data[key] === 'string') ||
      !['createdAt', 'updatedAt'].every(key => typeof data[key] === 'string' && Number.isFinite(Date.parse(data[key])))) {
    throw new Error('Unexpected service response')
  }
  return data as unknown as CatalogService
}
function base(businessId: string) { return `/api/businesses/${encodeURIComponent(businessId)}/services` }
export async function listServices(businessId: string, filters: ServiceFilters = defaultServiceFilters) {
  const params = new URLSearchParams({ search: filters.search, category: filters.category, active: String(filters.active), sort: filters.sort })
  const result = await requestJson(`${base(businessId)}?${params}`)
  if (!Array.isArray(result)) throw new Error('Unexpected service list')
  return result.map(value => parseService(value, businessId))
}
export async function getService(businessId: string, id: string) {
  return parseService(await requestJson(`${base(businessId)}/${encodeURIComponent(id)}`), businessId, id)
}
export async function saveService(businessId: string, input: ServiceInput, id?: string) {
  // Whitelist writable fields: never send business IDs, active flags or timestamps from a loaded entity.
  const { name, description, category, unit, unitPrice, currency, taxRate } = input
  return parseService(await requestJson(base(businessId) + (id ? `/${encodeURIComponent(id)}` : ''), {
    method: id ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, description, category, unit, unitPrice, currency, taxRate }),
  }), businessId, id)
}
export async function archiveService(businessId: string, id: string, archived: boolean) {
  return parseService(await requestJson(`${base(businessId)}/${encodeURIComponent(id)}/${archived ? 'archive' : 'restore'}`, { method: 'PATCH' }), businessId, id)
}
