import type { BusinessSettings, SettingsInput } from '../api/settings'

/**
 * Client-side mirror of the server rules (BusinessSettingsRequest). It only improves feedback:
 * the server normalises and validates again and remains authoritative.
 */
export type SettingsForm = Record<keyof SettingsInput, string>
export type SettingsErrors = Partial<Record<keyof SettingsInput | 'logo', string>>

export const settingsLimits = {
  name: 255, legalForm: 120, industry: 120, street: 255, postalCode: 20, city: 120, email: 320, phone: 40,
  website: 255, vatNumber: 40, iban: 42, bankName: 120, documentFooter: 160, brandColor: 7,
} as const
export const defaultBrandColor = '#245747'
export const logoTypes = ['image/png', 'image/jpeg'] as const
export const maxLogoBytes = 2 * 1024 * 1024

const fields: (keyof SettingsInput)[] = ['name', 'legalForm', 'industry', 'street', 'postalCode', 'city', 'country',
  'email', 'phone', 'website', 'vatNumber', 'iban', 'bankName', 'documentFooter', 'brandColor']

export function toForm(settings: BusinessSettings): SettingsForm {
  const form = Object.fromEntries(fields.map(field => [field, settings[field] ?? ''])) as SettingsForm
  return { ...form, iban: settings.iban ? formatIban(settings.iban) : '' }
}

const optional = (value: string) => value.trim() || null
export function compactIban(value: string) { return value.replace(/\s+/g, '').toUpperCase() }
/** "CH9300762011623852957" → "CH93 0076 2011 6238 5295 7" */
export function formatIban(value: string) { return compactIban(value).replace(/(.{4})(?!$)/g, '$1 ') }

const swissUid = /^CHE(\d{9})(MWST|TVA|IVA)?$/
/** "che 116 281 710 mwst" → "CHE-116.281.710 MWST"; other numbers are only upper-cased. */
export function formatVat(value: string) {
  const upper = value.trim().toUpperCase()
  const match = swissUid.exec(upper.replace(/[\s.-]/g, ''))
  if (!match) return upper
  const digits = match[1]
  const formatted = `CHE-${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6)}`
  return match[2] ? `${formatted} ${match[2]}` : formatted
}

export function toInput(form: SettingsForm): SettingsInput {
  return {
    name: form.name.trim(), legalForm: optional(form.legalForm), industry: optional(form.industry),
    street: optional(form.street), postalCode: optional(form.postalCode), city: optional(form.city), country: form.country,
    email: optional(form.email), phone: optional(form.phone), website: optional(form.website),
    vatNumber: optional(form.vatNumber) && formatVat(form.vatNumber), iban: optional(form.iban) && compactIban(form.iban),
    bankName: optional(form.bankName), documentFooter: optional(form.documentFooter),
    brandColor: optional(form.brandColor) && form.brandColor.trim().toUpperCase(),
  }
}

/** eCH-0097: weights 5 4 3 2 7 6 5 4, modulus 11. */
export function swissUidValid(digits: string) {
  const weights = [5, 4, 3, 2, 7, 6, 5, 4]
  const sum = weights.reduce((total, weight, index) => total + Number(digits[index]) * weight, 0)
  const check = (11 - sum % 11) % 11
  return check !== 10 && check === Number(digits[8])
}

const ibanLengths: Record<string, number> = { CH: 21, LI: 21, DE: 22, AT: 20, FR: 27, IT: 27, NL: 18, BE: 16, LU: 20, ES: 24, PT: 25, GB: 22 }
export function ibanValid(value: string) {
  const iban = compactIban(value)
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(iban)) return false
  const length = ibanLengths[iban.slice(0, 2)]
  if (length && iban.length !== length) return false
  const numeric = [...iban.slice(4) + iban.slice(0, 4)].map(character => parseInt(character, 36)).join('')
  let remainder = 0
  for (const digit of numeric) remainder = (remainder * 10 + Number(digit)) % 97
  return remainder === 1
}

/** WCAG contrast of a colour against white; the accent prints text, so it must reach 4.5:1. */
export function contrastOnWhite(hex: string) {
  const rgb = parseInt(hex.slice(1), 16)
  const channel = (value: number) => {
    const c = (value & 0xff) / 255
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
  }
  const luminance = 0.2126 * channel(rgb >> 16) + 0.7152 * channel(rgb >> 8) + 0.0722 * channel(rgb)
  return 1.05 / (luminance + 0.05)
}

const singleLine = (value: string) => !/\p{Cc}/u.test(value)

export function validateSettings(form: SettingsForm): SettingsErrors {
  const errors: SettingsErrors = {}
  const input = toInput(form)
  for (const field of Object.keys(settingsLimits) as (keyof typeof settingsLimits)[]) {
    if (form[field].trim().length > settingsLimits[field]) errors[field] = `Höchstens ${settingsLimits[field]} Zeichen.`
  }
  for (const field of ['name', 'legalForm', 'industry', 'street', 'city', 'bankName', 'documentFooter'] as const) {
    if (!errors[field] && !singleLine(form[field])) errors[field] = 'Bitte ohne Zeilenumbrüche.'
  }
  if (!input.name) errors.name = 'Bitte gib den Namen deines Unternehmens an.'
  if (!/^[A-Z]{2}$/.test(input.country)) errors.country = 'Bitte wähle ein Land.'
  if (input.postalCode && !errors.postalCode && !/^[\p{L}\p{N}][\p{L}\p{N} -]*$/u.test(input.postalCode)) errors.postalCode = 'Bitte prüfe die Postleitzahl.'
  if (input.email && !errors.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email)) errors.email = 'Bitte gib eine gültige E-Mail-Adresse an.'
  if (input.phone && !errors.phone) {
    const digits = input.phone.replace(/\D/g, '').length
    if (!/^\+?\d[\d ()./-]*$/.test(input.phone) || digits < 5 || digits > 15) errors.phone = 'Bitte gib eine gültige Telefonnummer an, z. B. +41 44 123 45 67.'
  }
  if (input.website && !errors.website && !/^(https?:\/\/)?([a-z0-9](-*[a-z0-9])*\.)+[a-z]{2,63}(:\d{1,5})?(\/\S*)?$/i.test(input.website)) {
    errors.website = 'Bitte gib eine Webadresse an, z. B. www.beispiel.ch.'
  }
  if (input.vatNumber && !errors.vatNumber) {
    const swiss = swissUid.exec(input.vatNumber.replace(/[\s.-]/g, ''))
    const valid = swiss ? swissUidValid(swiss[1]) : !input.vatNumber.startsWith('CHE') && /^[A-Z]{2}[A-Z0-9 ./-]{2,38}$/.test(input.vatNumber)
    if (!valid) errors.vatNumber = 'Bitte prüfe die Nummer, z. B. CHE-123.456.789 MWST.'
  }
  if (input.iban && !errors.iban && !ibanValid(input.iban)) errors.iban = 'Diese IBAN ist ungültig. Bitte prüfe sie.'
  if (input.brandColor && !errors.brandColor) {
    if (!/^#[0-9A-F]{6}$/.test(input.brandColor)) errors.brandColor = 'Bitte gib eine Farbe im Format #1F3A5F an.'
    else if (contrastOnWhite(input.brandColor) < 4.5) errors.brandColor = 'Diese Farbe ist auf weissem Papier zu hell. Bitte wähle einen dunkleren Ton.'
  }
  return errors
}

export function logoProblem(file: { type: string; size: number }): string | null {
  if (!(logoTypes as readonly string[]).includes(file.type)) return 'Bitte wähle eine PNG- oder JPEG-Datei.'
  if (file.size === 0) return 'Die Datei ist leer.'
  if (file.size > maxLogoBytes) return 'Das Logo darf höchstens 2 MB gross sein.'
  return null
}

/** Server field errors carry no details; show the specific client message where one exists. */
export function serverErrors(fields: Record<string, string>, form: SettingsForm): SettingsErrors {
  const local = validateSettings(form)
  const errors: SettingsErrors = {}
  for (const [field, message] of Object.entries(fields)) {
    if (field === 'logo') errors.logo = message
    else if (field in settingsLimits || field === 'country') errors[field as keyof SettingsInput] = local[field as keyof SettingsInput] ?? 'Bitte prüfe dieses Feld.'
  }
  return errors
}

export function formatBytes(bytes: number) {
  return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
