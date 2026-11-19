import { requestJson } from './client'

/** Editable business settings. Optional values are null when not set; the currency is read-only. */
export interface SettingsInput {
  name: string
  legalForm: string | null
  industry: string | null
  street: string | null
  postalCode: string | null
  city: string | null
  country: string
  email: string | null
  phone: string | null
  website: string | null
  vatNumber: string | null
  iban: string | null
  bankName: string | null
  documentFooter: string | null
  brandColor: string | null
}
export interface BusinessLogo { id: string; width: number; height: number; sizeBytes: number }
export interface BusinessSettings extends SettingsInput {
  id: string
  currency: string
  logo: BusinessLogo | null
  role: 'OWNER' | 'MEMBER'
  editable: boolean
  updatedAt: string
}

const optionalKeys = ['legalForm', 'industry', 'street', 'postalCode', 'city', 'email', 'phone', 'website',
  'vatNumber', 'iban', 'bankName', 'documentFooter', 'brandColor'] as const
const uuid = /^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i

function parseLogo(value: unknown): BusinessLogo | null {
  if (value === null) return null
  if (typeof value !== 'object' || value === undefined) throw new Error('Unexpected settings response')
  const data = value as Record<string, unknown>
  if (typeof data.id !== 'string' || !uuid.test(data.id) ||
      !['width', 'height', 'sizeBytes'].every(key => Number.isInteger(data[key]) && (data[key] as number) > 0)) {
    throw new Error('Unexpected settings response')
  }
  return { id: data.id, width: data.width as number, height: data.height as number, sizeBytes: data.sizeBytes as number }
}

export function parseSettings(value: unknown): BusinessSettings {
  if (typeof value !== 'object' || value === null) throw new Error('Unexpected settings response')
  const data = value as Record<string, unknown>
  if (typeof data.id !== 'string' || !uuid.test(data.id) || typeof data.name !== 'string' || !data.name.trim() ||
      typeof data.country !== 'string' || !/^[A-Z]{2}$/.test(data.country) ||
      typeof data.currency !== 'string' || !/^[A-Z]{3}$/.test(data.currency) ||
      !optionalKeys.every(key => data[key] === null || typeof data[key] === 'string') ||
      (data.role !== 'OWNER' && data.role !== 'MEMBER') || typeof data.editable !== 'boolean' ||
      typeof data.updatedAt !== 'string' || !Number.isFinite(Date.parse(data.updatedAt))) {
    throw new Error('Unexpected settings response')
  }
  const optional = Object.fromEntries(optionalKeys.map(key => [key, data[key] as string | null])) as Pick<SettingsInput, typeof optionalKeys[number]>
  return {
    ...optional, id: data.id, name: data.name, country: data.country, currency: data.currency,
    logo: parseLogo(data.logo), role: data.role, editable: data.editable && data.role === 'OWNER', updatedAt: data.updatedAt,
  }
}

const base = (businessId: string) => `/api/businesses/${encodeURIComponent(businessId)}/settings`

export async function getSettings(businessId: string): Promise<BusinessSettings> {
  return parseSettings(await requestJson(base(businessId)))
}
export async function updateSettings(businessId: string, input: SettingsInput): Promise<BusinessSettings> {
  return parseSettings(await requestJson(base(businessId), {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
  }))
}
/** Sends the image as the raw request body; the server checks type, signature and dimensions again. */
export async function uploadLogo(businessId: string, file: Blob): Promise<BusinessSettings> {
  return parseSettings(await requestJson(`${base(businessId)}/logo`, {
    method: 'PUT', headers: { 'Content-Type': file.type }, body: file,
  }))
}
export async function removeLogo(businessId: string): Promise<BusinessSettings> {
  return parseSettings(await requestJson(`${base(businessId)}/logo`, { method: 'DELETE' }))
}
/** Same-origin, session-authenticated image URL; the logo ID busts the browser cache after a change. */
export function logoUrl(businessId: string, logo: BusinessLogo) {
  return `${base(businessId)}/logo?v=${encodeURIComponent(logo.id)}`
}
