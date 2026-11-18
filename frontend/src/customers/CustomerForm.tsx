import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { Customer, CustomerInput } from '@/api/customers'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Input, Select, Textarea, FormField } from '@/components/ui/forms'
import { validateCustomer } from './validation'

export function CustomerForm({ customer, save }: { customer?: Customer; save: (input: CustomerInput) => Promise<void> }) {
  const [form, setForm] = useState<CustomerInput>(customer ?? { type: 'PRIVATE', displayName: '', firstName: null, lastName: null, companyName: null, email: null, phone: null, street: null, postalCode: null, city: null, country: 'CH', notes: null })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const ref = useRef<HTMLFormElement>(null)
  function field(key: keyof CustomerInput, value: string) {
    setForm(previous => ({ ...previous, [key]: value }))
    setErrors(previous => ({ ...previous, [key]: '' }))
  }
  function input(key: Exclude<keyof CustomerInput, 'type' | 'notes'>, label: string, required = false) {
    return <FormField label={label} error={errors[key]}><Input name={key} type={key === 'email' ? 'email' : key === 'phone' ? 'tel' : 'text'} required={required} disabled={pending} value={form[key] ?? ''} onChange={event => field(key, event.target.value)} /></FormField>
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy.current) return
    const data = Object.fromEntries(Object.entries(form).filter(([key]) => ['type', 'displayName', 'companyName', 'firstName', 'lastName', 'email', 'phone', 'street', 'postalCode', 'city', 'country', 'notes'].includes(key)).map(([key, value]) => [key, typeof value === 'string' ? value.trim() || null : value])) as unknown as CustomerInput
    data.country = form.country.trim().toUpperCase()
    data.displayName = form.displayName.trim()
    if (data.type === 'PRIVATE') data.companyName = null
    const validation = validateCustomer(data)
    setErrors(validation); setError('')
    if (Object.keys(validation).length) {
      requestAnimationFrame(() => ref.current?.querySelector<HTMLElement>(`[name="${Object.keys(validation)[0]}"]`)?.focus())
      return
    }
    busy.current = true; setPending(true)
    try { await save(data) }
    catch (error) {
      if (error instanceof ApiError && error.status === 400 && Object.keys(error.fields).length) setErrors(error.fields)
      else setError(error instanceof ApiError && error.status === 409 ? 'Entferne zuerst die Kontaktpersonen, bevor du den Kundentyp auf Privatperson änderst.' : error instanceof ApiError && error.status === 404 ? 'Dieser Kunde oder das Unternehmen ist nicht mehr verfügbar.' : 'Speichern konnte nicht bestätigt werden. Bitte prüfe deine Verbindung und lade die Liste vor einem erneuten Versuch neu.')
    } finally { busy.current = false; setPending(false) }
  }
  return <form ref={ref} className="customer-form" noValidate onSubmit={submit} aria-busy={pending}>
    {error && <p role="alert" className="auth-error">{error}</p>}
    <FormField label="Kundentyp" error={errors.type}><Select value={form.type} disabled={pending} onChange={event => field('type', event.target.value)}><option value="PRIVATE">Privatperson</option><option value="COMPANY">Unternehmen</option></Select></FormField>
    {input('displayName', 'Kundenname', true)}
    {form.type === 'COMPANY' && input('companyName', 'Firmenname (optional)')}
    <div className="form-grid">{input('firstName', form.type === 'PRIVATE' ? 'Vorname' : 'Kontaktperson: Vorname')}{input('lastName', form.type === 'PRIVATE' ? 'Nachname' : 'Kontaktperson: Nachname')}{input('email', 'E-Mail')}{input('phone', 'Telefonnummer')}</div>
    {input('street', 'Strasse und Hausnummer')}
    <div className="form-grid">{input('postalCode', 'Postleitzahl')}{input('city', 'Ort')}</div>
    <FormField label="Land" error={errors.country} hint="Zweistelliger Ländercode, z. B. CH, DE oder AT."><Input name="country" value={form.country} disabled={pending} onChange={event => field('country', event.target.value)} /></FormField>
    <FormField label="Notizen (optional)" error={errors.notes}><Textarea name="notes" value={form.notes ?? ''} disabled={pending} onChange={event => field('notes', event.target.value)} /></FormField>
    <Button type="submit" disabled={pending}>{pending ? 'Wird gespeichert…' : customer ? 'Änderungen speichern' : 'Kunde erstellen'}</Button>
  </form>
}
