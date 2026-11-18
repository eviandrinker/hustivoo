import { useId, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react'
import type { Quote, QuoteInput } from '@/api/quotes'
import type { Customer } from '@/api/customers'
import type { CatalogService } from '@/api/services'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { FormField, Input, Select, Textarea } from '@/components/ui/forms'
import { unitLabels } from '@/services/validation'
import { QuoteSummary } from './QuoteParts'
import { formatMoney, previewTotals } from './money'
import { draftFromQuote, emptyDraft, freeItem, itemFromService, MAX_ITEMS, moveItem, normalizeDraft, validateQuote } from './validation'
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
  const [catalogChoice, setCatalogChoice] = useState('')
  const busy = useRef(false)
  const form = useRef<HTMLFormElement>(null)
  const ids = { customer: useId(), items: useId(), texts: useId(), catalog: useId() }
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
  function addFromCatalog() {
    const service = services.find(item => item.id === catalogChoice)
    if (!service || draft.items.length >= MAX_ITEMS) return
    setItems([...draft.items, itemFromService(service)], `[data-item="${draft.items.length}"] textarea`)
    setCatalogChoice('')
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

  const itemError = (index: number, key: string) => errors[`items[${index}].${key}`]
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

      <section className="quote-card" aria-labelledby={ids.items}>
        <div className="quote-card-heading"><h2 id={ids.items}>Positionen</h2><span>{draft.items.length} {draft.items.length === 1 ? 'Position' : 'Positionen'}</span></div>
        {errors.items && <p className="field-error" role="alert">{errors.items}</p>}
        {draft.items.length ? <ol className="quote-items">{draft.items.map((item, index) => {
          const line = preview.lines[index]
          return <li key={item.key} data-item={index}>
            <fieldset className="quote-item" disabled={pending}>
              <legend className="sr-only">Position {index + 1}</legend>
              <div className="quote-item-head">
                <span className="quote-item-index" aria-hidden="true">{index + 1}</span>
                <span className="quote-item-source">{item.serviceId ? 'Aus Leistungskatalog' : 'Freie Position'}</span>
                <div className="quote-item-tools">
                  <Button variant="tertiary" aria-label={`Position ${index + 1} nach oben`} disabled={index === 0} onClick={() => setItems(moveItem(draft.items, index, -1))}><ArrowUp size={16} aria-hidden="true" /></Button>
                  <Button variant="tertiary" aria-label={`Position ${index + 1} nach unten`} disabled={index === draft.items.length - 1} onClick={() => setItems(moveItem(draft.items, index, 1))}><ArrowDown size={16} aria-hidden="true" /></Button>
                  <Button variant="tertiary" className="quote-item-remove" aria-label={`Position ${index + 1} entfernen`} onClick={() => setItems(draft.items.filter((_, position) => position !== index), '#quote-add-free')}><Trash2 size={16} aria-hidden="true" /></Button>
                </div>
              </div>
              <FormField label="Beschreibung" error={itemError(index, 'description')}><Textarea name={`items[${index}].description`} rows={2} value={item.description} placeholder="Was wird geleistet?" onChange={event => setItem(index, 'description', event.target.value)} /></FormField>
              <div className="quote-item-grid">
                <FormField label="Menge" error={itemError(index, 'quantity')}><Input name={`items[${index}].quantity`} inputMode="decimal" value={item.quantity} onChange={event => setItem(index, 'quantity', event.target.value)} /></FormField>
                <FormField label="Einheit" error={itemError(index, 'unit')}><Select name={`items[${index}].unit`} value={item.unit} onChange={event => setItem(index, 'unit', event.target.value)}>{Object.entries(unitLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select></FormField>
                <FormField label={`Einzelpreis (${currency})`} error={itemError(index, 'unitPrice')}><Input name={`items[${index}].unitPrice`} inputMode="decimal" placeholder="0.00" value={item.unitPrice} onChange={event => setItem(index, 'unitPrice', event.target.value)} /></FormField>
                <FormField label="MWST %" error={itemError(index, 'taxRate')}><Input name={`items[${index}].taxRate`} inputMode="decimal" value={item.taxRate} onChange={event => setItem(index, 'taxRate', event.target.value)} /></FormField>
                <div className="quote-item-amount" aria-live="polite"><span>Betrag netto</span><strong>{line ? formatMoney(line.subtotal, currency) : '—'}</strong></div>
              </div>
              {itemError(index, 'serviceId') && <p className="field-error" role="alert">Diese Leistung ist nicht mehr verfügbar. Entferne die Position oder wähle eine andere Leistung.</p>}
            </fieldset>
          </li>
        })}</ol> : <p className="quote-empty-items">Noch keine Positionen. Füge Leistungen aus deinem Katalog oder freie Positionen hinzu.</p>}
        <div className="quote-add">
          <div className="quote-add-catalog">
            <label htmlFor={ids.catalog}>Leistung aus Katalog</label>
            <div>
              <Select id={ids.catalog} name="catalogService" value={catalogChoice} disabled={pending || !services.length} onChange={event => setCatalogChoice(event.target.value)}>
                <option value="">{services.length ? 'Leistung auswählen…' : 'Keine aktiven Leistungen'}</option>
                {services.map(service => <option key={service.id} value={service.id} disabled={service.currency !== currency}>
                  {service.name} · {formatMoney(service.unitPrice, service.currency)} / {unitLabels[service.unit]}{service.currency !== currency ? ` (nur ${service.currency})` : ''}
                </option>)}
              </Select>
              <Button variant="secondary" disabled={pending || !catalogChoice || draft.items.length >= MAX_ITEMS} onClick={addFromCatalog}><Plus size={16} aria-hidden="true" />Hinzufügen</Button>
            </div>
          </div>
          <Button id="quote-add-free" variant="tertiary" disabled={pending || draft.items.length >= MAX_ITEMS} onClick={() => setItems([...draft.items, freeItem(draft.items.at(-1))], `[data-item="${draft.items.length}"] textarea`)}><Plus size={16} aria-hidden="true" />Freie Position</Button>
        </div>
      </section>

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
