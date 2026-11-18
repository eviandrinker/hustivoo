import type { CustomerInput } from '../api/customers'
export const customerLimits = { displayName: 255, companyName: 255, firstName: 120, lastName: 120, email: 320, phone: 50, street: 255, postalCode: 20, city: 120, notes: 4000 } as const
export function validateCustomer(input: CustomerInput) {
  const errors: Record<string, string> = {}
  if (!input.displayName.trim()) errors.displayName = 'Bitte gib einen Kundennamen ein.'
  for (const [key, limit] of Object.entries(customerLimits)) {
    if ((input[key as keyof typeof customerLimits]?.length ?? 0) > limit) errors[key] = `Maximal ${limit} Zeichen.`
  }
  if (input.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email)) errors.email = 'Bitte gib eine gültige E-Mail-Adresse ein.'
  if (!/^[A-Z]{2}$/.test(input.country)) errors.country = 'Bitte gib einen gültigen Ländercode ein.'
  return errors
}

export function validateContact(input: import('../api/customers').ContactInput) {
  const errors: Record<string, string> = {}
  for (const key of ['firstName', 'lastName'] as const) if (!input[key].trim()) errors[key] = 'Bitte fülle dieses Feld aus.'
  for (const [key, max] of Object.entries({ firstName: 120, lastName: 120, jobTitle: 120, email: 320, phone: 50 })) {
    if (String(input[key as keyof typeof input] ?? '').length > max) errors[key] = `Maximal ${max} Zeichen.`
  }
  if (input.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input.email)) errors.email = 'Bitte gib eine gültige E-Mail-Adresse ein.'
  return errors
}
export function validateNote(content: string): string | undefined {
  if (!content.trim()) return 'Bitte schreibe eine Notiz.'
  if (content.trim().length > 4000) return 'Maximal 4000 Zeichen.'
}
