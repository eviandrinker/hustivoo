import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { Contact, ContactInput } from '@/api/customers'
import { ApiError } from '@/api/client'
import { FormField, Input, Checkbox } from '@/components/ui/forms'
import { Button } from '@/components/ui/button'
import { validateContact } from './validation'

export function ContactForm({ contact, save }: { contact?: Contact; save: (input: ContactInput) => Promise<void> }) {
  const [form, setForm] = useState<ContactInput>(contact ?? { firstName: '', lastName: '', jobTitle: null, email: null, phone: null, primaryContact: false })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const ref = useRef<HTMLFormElement>(null)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy.current) return
    const input: ContactInput = { firstName: form.firstName.trim(), lastName: form.lastName.trim(), jobTitle: form.jobTitle?.trim() || null, email: form.email?.trim() || null, phone: form.phone?.trim() || null, primaryContact: form.primaryContact }
    const invalid = validateContact(input)
    setErrors(invalid); setError('')
    if (Object.keys(invalid).length) { requestAnimationFrame(() => ref.current?.querySelector<HTMLElement>(`[name="${Object.keys(invalid)[0]}"]`)?.focus()); return }
    busy.current = true; setPending(true)
    try { await save(input) }
    catch (error) {
      if (error instanceof ApiError && error.status === 400 && Object.keys(error.fields).length) setErrors(error.fields)
      else setError('Die Kontaktperson konnte nicht gespeichert werden. Bitte lade die Daten erneut.')
    } finally { busy.current = false; setPending(false) }
  }
  return <form ref={ref} className="customer-form" onSubmit={submit} noValidate aria-busy={pending}>
    {error && <p role="alert" className="auth-error">{error}</p>}
    {([['firstName', 'Vorname'], ['lastName', 'Nachname'], ['jobTitle', 'Funktion (optional)'], ['email', 'E-Mail (optional)'], ['phone', 'Telefon (optional)']] as const).map(([key,label]) => <FormField key={key} label={label} error={errors[key]}><Input name={key} value={form[key] ?? ''} type={key === 'email' ? 'email' : key === 'phone' ? 'tel' : 'text'} disabled={pending} required={key === 'firstName' || key === 'lastName'} onChange={event => { setForm(previous => ({ ...previous, [key]: event.target.value })); setErrors(previous => ({ ...previous, [key]: '' })) }} /></FormField>)}
    <label className="archive-toggle"><Checkbox checked={form.primaryContact} disabled={pending} onChange={event => setForm(previous => ({ ...previous, primaryContact: event.target.checked }))} />Hauptansprechpartner</label>
    <p className="field-hint">Ein bisheriger Hauptansprechpartner wird bei Auswahl automatisch abgelöst.</p>
    <Button type="submit" disabled={pending}>{pending ? 'Wird gespeichert…' : 'Kontaktperson speichern'}</Button>
  </form>
}
