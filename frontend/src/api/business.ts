import { requestJson } from './client'

export interface Business {
  id: string
  name: string
  industry: string | null
  legalForm: string | null
  country: string
  currency: string
  createdAt: string
  updatedAt: string
}
export interface CreateBusiness {
  name: string
  industry?: string | null
  legalForm?: string | null
  country: string
  currency: string
}
function parseBusiness(value: unknown): Business {
  if (typeof value !== 'object' || value === null) throw new Error('Unexpected business response')
  const data = value as Record<string, unknown>
  if (typeof data.id !== 'string' || !/^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i.test(data.id) ||
      typeof data.name !== 'string' || !data.name.trim() ||
      !['industry', 'legalForm'].every(key => data[key] === null || typeof data[key] === 'string') ||
      typeof data.country !== 'string' || !/^[A-Z]{2}$/.test(data.country) ||
      typeof data.currency !== 'string' || !/^[A-Z]{3}$/.test(data.currency) ||
      !['createdAt', 'updatedAt'].every(key => typeof data[key] === 'string' && Number.isFinite(Date.parse(data[key])))) {
    throw new Error('Unexpected business response')
  }
  return { id: data.id, name: data.name, industry: data.industry as string | null,
    legalForm: data.legalForm as string | null, country: data.country, currency: data.currency,
    createdAt: data.createdAt as string, updatedAt: data.updatedAt as string }
}
export async function listBusinesses(): Promise<Business[]> {
  const data = await requestJson('/api/businesses')
  if (!Array.isArray(data)) throw new Error('Unexpected business list')
  const businesses = data.map(parseBusiness)
  if (new Set(businesses.map(item => item.id)).size !== businesses.length) throw new Error('Duplicate business')
  return businesses
}
export async function getBusiness(id: string): Promise<Business> {
  return parseBusiness(await requestJson(`/api/businesses/${encodeURIComponent(id)}`))
}
export async function createBusiness(input: CreateBusiness): Promise<Business> {
  return parseBusiness(await requestJson('/api/businesses', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
  }))
}
