import { useId, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { Quote, QuoteInput } from '@/api/quotes'
import type { Customer } from '@/api/customers'
import type { CatalogService } from '@/api/services'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { FormField, Input, Select, Textarea } from '@/components/ui/forms'
import { PositionsEditor } from '@/documents/PositionsEditor'
import { QuoteSummary } from './QuoteParts'
import { previewTotals } from './money'
import { draftFromQuote, emptyDraft, normalizeDraft, validateQuote } from './validation'
import type { DraftItem, QuoteDraft } from './validation'

const CURRENCIES = ['CHF', 'EUR', 'USD', 'GBP']

export function QuoteEditorForm({ quote, customers, services, defaults, save, onCancel, onDirtyChange }: {
  quote?: Quote
  customers: Customer[]
  services: CatalogService[]
  defaults: { today: string; currency: string }
  save: (input: QuoteInput) => Promise<void>
  onCancel: () => void
  onDirtyChange?: (dirty: boolean) => void
}) {
  const [draft, setDraft] = useState<QuoteDraft>(() => quote ? draftFromQuote(quote) : emptyDraft(defaults.today, defaults.currency))
  const initial = useRef(JSON.stringify(normalizeDraft(draft)))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const form = useRef<HTMLFormElement>(null)
  const ids = { customer: useId(), texts: useId() }
  const preview = useMemo(() => previewTotals(normalizeDraft(draft).items, draft.currency.trim().toUpperCase()), [draft])

  // Archived customers are not offered for new quotes, but a draft keeps the one it already uses.
  const customerOptions = useMemo(() => {
    const options = customers.filter(customer => !customer.archived).map(customer => ({ id: customer.id, label: `${customer.displayName} · ${customer.customerNumber}` }))
    if (quote && !options.some(option => option.id === quote.customerId)) options.unshift({ id: quote.customerId, label: `${quote.customer.name} · ${quote.customer.customerNumber} (archiviert)` })
    return options
  }, [customers, quote])
  const currency = draft.currency.trim().toUpperCase()
  const currencies = CURRENCIES.includes(currency) ? CURRENCIES : [...CURRENCIES, currency]

  function update(next: QuoteDraft, clear: string[] = []) {
    setDraft(next)
    onDirtyChange?.(JSON.stringify(normalizeDraft(next)) !== initial.current)
    if (clear.length) setErrors(previous => Object.fromEntries(Object.entries(previous).filter(([key]) => !clear.includes(key) && key !== 'items')))
  }
  function setField<K extends keyof Omit<QuoteDraft, 'items'>>(key: K, value: QuoteDraft[K]) {
    update({ ...draft, [key]: value }, [key])
  }
  function setItem(index: number, key: keyof Omit<DraftItem, 'key' | 'serviceId'>, value: string) {
    update({ ...draft, items: draft.items.map((item, position) => position === index ? { ...item, [key]: value } : item) }, [`items[${index}].${key}`])
  }
  // Structural changes renumber positions, so position-bound errors are cleared.
  function setItems(items: DraftItem[], focus?: string) {
    update({ ...draft, items })
    setErrors(previous => Object.fromEntries(Object.entries(previous).filter(([key]) => !key.startsWith('items'))))
    if (focus) requestAnimationFrame(() => form.current?.querySelector<HTMLElement>(focus)?.focus())
  }
  function focusInvalid() { requestAnimationFrame(() => form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()) }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy.current) return
    const input = normalizeDraft(draft)
    const invalid = validateQuote(input)
    setErrors(invalid); setError('')
    if (Object.keys(invalid).length) { focusInvalid(); return }
    busy.current = true; setPending(true)
    try { await save(input) }
    catch (failure) {
      if (failure instanceof ApiError && failure.status === 400 && Object.keys(failure.fields).length) {
        setErrors(failure.fields)
        setError(failure.fields.customerId || Object.keys(failure.fields).some(key => key.endsWith('serviceId'))
          ? 'Kunde oder Leistung ist nicht mehr verfügbar. Bitte wähle neu aus.' : 'Bitte prüfe die markierten Felder.')
        focusInvalid()
      } else if (failure instanceof ApiError && failure.status === 409) {
        setError('Das Angebot wurde inzwischen versendet und kann nicht mehr bearbeitet werden. Bitte lade es neu.')
      } else if (failure instanceof ApiError && failure.status === 403) {
        setError('Die Anfrage wurde abgelehnt. Bitte lade die Seite neu und versuche es erneut.')
      } else {
        setError('Das Speichern konnte nicht bestätigt werden. Prüfe die Angebotsliste, bevor du es erneut versuchst.')
      }
    } finally { busy.current = false; setPending(false) }
  }

  return <form ref={form} className="quote-editor" onSubmit={submit} noValidate aria-busy={pending}>
    <div className="quote-editor-main">
      {error && <p className="auth-error" role="alert">{error}</p>}

      <fieldset className="quote-card" disabled={pending} aria-labelledby={ids.customer}>
        <h2 id={ids.customer}>Kunde und Konditionen</h2>
        <FormField label="Kunde" error={errors.customerId} hint={customerOptions.length ? undefined : 'Lege zuerst einen Kunden an.'}>
          <Select name="customerId" required value={draft.customerId} onChange={event => setField('customerId', event.target.value)}>
            <option value="">Kunde auswählen…</option>
            {customerOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
          </Select>
        </FormField>
        <div className="quote-grid quote-grid--3">
          <FormField label="Ausstellungsdatum" error={errors.issueDate}><Input type="date" name="issueDate" required value={draft.issueDate} onChange={event => setField('issueDate', event.target.value)} /></FormField>
          <FormField label="Gültig bis" error={errors.validUntil}><Input type="date" name="validUntil" required min={draft.issueDate} value={draft.validUntil} onChange={event => setField('validUntil', event.target.value)} /></FormField>
          <FormField label="Währung" error={errors.currency} hint={draft.items.length ? 'Preise werden nicht umgerechnet.' : undefined}>
            <Select name="currency" value={currency} onChange={event => setField('currency', event.target.value)}>{currencies.map(code => <option key={code} value={code}>{code}</option>)}</Select>
          </FormField>
        </div>
      </fieldset>

      <PositionsEditor items={draft.items} currency={currency} services={services} pending={pending} errors={errors}
        lines={preview.lines} addFreeId="quote-add-free" onItems={setItems} onItem={setItem} />

      <fieldset className="quote-card" disabled={pending} aria-labelledby={ids.texts}>
        <h2 id={ids.texts}>Texte</h2>
        <FormField label="Einleitungstext (optional)" error={errors.introductionText}><Textarea name="introductionText" rows={3} value={draft.introductionText} placeholder="Vielen Dank für Ihre Anfrage. Gerne unterbreiten wir Ihnen folgendes Angebot:" onChange={event => setField('introductionText', event.target.value)} /></FormField>
        <FormField label="Abschlusstext (optional)" error={errors.closingText}><Textarea name="closingText" rows={3} value={draft.closingText} placeholder="Wir freuen uns auf Ihre Rückmeldung." onChange={event => setField('closingText', event.target.value)} /></FormField>
        <FormField label="Interne Notizen (optional)" hint="Nur intern sichtbar, erscheint nicht im PDF." error={errors.notes}><Textarea name="notes" rows={2} value={draft.notes} onChange={event => setField('notes', event.target.value)} /></FormField>
      </fieldset>
    </div>

    <aside className="quote-editor-aside">
      <QuoteSummary currency={currency} subtotal={preview.subtotal} taxTotal={preview.taxTotal} grandTotal={preview.grandTotal} taxGroups={preview.taxGroups}
        note={preview.complete ? 'Vorschau. Die verbindlichen Beträge berechnet Hustivoo beim Speichern.' : 'Unvollständige Positionen sind noch nicht berücksichtigt.'}>
        <Button type="submit" disabled={pending}>{pending ? 'Wird gespeichert…' : quote ? 'Änderungen speichern' : 'Entwurf speichern'}</Button>
        <Button variant="secondary" disabled={pending} onClick={onCancel}>Abbrechen</Button>
      </QuoteSummary>
    </aside>
  </form>
}
