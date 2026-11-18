import { useRef, useState, useId } from 'react'
import type { FormEvent, ComponentProps } from 'react'
import type { CatalogService, ServiceInput } from '@/api/services'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { FormField, Input, Select, Textarea } from '@/components/ui/forms'
import { normalizeService, validateService, unitLabels } from './validation'

export function ServiceForm({ service, save, onCancel, onDirtyChange }: { service?: CatalogService; save: (input: ServiceInput) => Promise<void>; onCancel?: () => void; onDirtyChange?: (dirty: boolean) => void }) {
  const [form, setForm] = useState<ServiceInput>(service ?? { name: '', description: null, category: null, unit: 'HOUR', unitPrice: '', currency: 'CHF', taxRate: null })
  const currencyListId = useId()
  const initial = useRef(normalizeService(form))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const ref = useRef<HTMLFormElement>(null)
  function change(key: keyof ServiceInput, value: string) {
    const next = { ...form, [key]: value }
    setForm(next)
    onDirtyChange?.(JSON.stringify(normalizeService(next)) !== JSON.stringify(initial.current))
    setErrors(previous => ({ ...previous, [key]: '' }))
  }
  function focusInvalid() { requestAnimationFrame(() => ref.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()) }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy.current) return
    const input = normalizeService(form)
    const invalid = validateService(input)
    setErrors(invalid); setError('')
    if (Object.keys(invalid).length) { focusInvalid(); return }
    busy.current = true; setPending(true)
    try { await save(input) }
    catch (error) {
      if (error instanceof ApiError && error.status === 400 && Object.keys(error.fields).length) { setErrors(error.fields); focusInvalid() }
      else setError(error instanceof ApiError && error.status === 403
        ? 'Die Anfrage wurde abgelehnt. Bitte lade die Seite neu und versuche es erneut.'
        : 'Das Speichern konnte nicht bestätigt werden. Bitte schliesse den Dialog und lade die Liste erneut, bevor du es nochmals versuchst.')
    } finally { busy.current = false; setPending(false) }
  }
  return <form ref={ref} className="service-form" onSubmit={submit} noValidate aria-busy={pending}>
    <div className="service-form-content">
      {error && <p className="auth-error" role="alert">{error}</p>}
      <fieldset className="service-section" disabled={pending}>
        <legend>Allgemein</legend>
        <FormField label="Name" error={errors.name}><Input name="name" value={form.name} required placeholder="Zum Beispiel Strategieberatung" onChange={event => change('name', event.target.value)} /></FormField>
        <FormField label="Kategorie (optional)" error={errors.category}><Input name="category" value={form.category ?? ''} placeholder="Zum Beispiel Beratung" onChange={event => change('category', event.target.value)} /></FormField>
        <FormField label="Beschreibung (optional)" error={errors.description}><Textarea name="description" value={form.description ?? ''} rows={3} placeholder="Was umfasst diese Leistung?" onChange={event => change('description', event.target.value)} /></FormField>
      </fieldset>
      <fieldset className="service-section" disabled={pending}>
        <legend>Preisgestaltung</legend>
        <div className="service-form-grid">
          <FormField label="Preis pro Einheit" error={errors.unitPrice}><PriceInput currency={form.currency.trim().toUpperCase()} name="unitPrice" value={form.unitPrice} inputMode="decimal" required placeholder="100.00" onChange={event => change('unitPrice', event.target.value)} /></FormField>
          <FormField label="Einheit" error={errors.unit}><Select name="unit" value={form.unit} onChange={event => change('unit', event.target.value)}>{Object.entries(unitLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select></FormField>
          <FormField label="Währung" error={errors.currency}><Input name="currency" list={currencyListId} value={form.currency} required maxLength={3} onChange={event => change('currency', event.target.value)} /></FormField>
          <FormField label="Steuersatz in % (optional)" error={errors.taxRate}><Input name="taxRate" value={form.taxRate ?? ''} inputMode="decimal" placeholder="Nicht festgelegt" onChange={event => change('taxRate', event.target.value)} /></FormField>
        </div>
      </fieldset>
      <datalist id={currencyListId}><option value="CHF">Schweizer Franken</option><option value="EUR">Euro</option><option value="USD">US-Dollar</option><option value="GBP">Britisches Pfund</option></datalist>
    </div>
    <div className="service-form-footer">
      <Button type="button" variant="secondary" disabled={pending} onClick={onCancel}>Abbrechen</Button>
      <Button type="submit" disabled={pending}>{pending ? 'Wird gespeichert…' : service ? 'Änderungen speichern' : 'Leistung erstellen'}</Button>
    </div>
  </form>
}

// Forward FormField's id and ARIA attributes to the actual input, not its wrapper.
function PriceInput({ currency, ...props }: ComponentProps<'input'> & { currency: string }) {
  return <div className="service-price-input"><span aria-hidden="true">{currency}</span><Input {...props} /></div>
}
