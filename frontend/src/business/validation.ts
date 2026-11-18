import type { CreateBusiness } from '../api/business'
export function validateBusiness(input: CreateBusiness): Partial<Record<keyof CreateBusiness, string>> {
  const errors: Partial<Record<keyof CreateBusiness, string>> = {}
  if (!input.name.trim()) errors.name = 'Bitte gib einen Unternehmensnamen ein.'
  else if (input.name.trim().length > 255) errors.name = 'Der Unternehmensname darf maximal 255 Zeichen enthalten.'
  if ((input.industry?.trim().length ?? 0) > 120) errors.industry = 'Maximal 120 Zeichen.'
  if ((input.legalForm?.trim().length ?? 0) > 120) errors.legalForm = 'Maximal 120 Zeichen.'
  if (!/^[A-Z]{2}$/.test(input.country)) errors.country = 'Bitte gib einen zweistelligen ISO-Ländercode ein, z. B. CH.'
  if (!/^[A-Z]{3}$/.test(input.currency)) errors.currency = 'Bitte gib einen dreistelligen ISO-Währungscode ein, z. B. CHF.'
  return errors
}
