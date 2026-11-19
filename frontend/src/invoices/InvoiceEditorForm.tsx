import { useId, useMemo, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import type { Invoice, InvoiceInput } from '@/api/invoices'
import type { Customer } from '@/api/customers'
import type { CatalogService } from '@/api/services'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { FormField, Input, Select, Textarea } from '@/components/ui/forms'
import { PositionsEditor } from '@/documents/PositionsEditor'
import { QuoteSummary } from '@/quotes/QuoteParts'
import { formatDate, previewTotals } from '@/quotes/money'
import type { DraftItem } from '@/quotes/validation'
import { draftFromInvoice, dueDate, emptyInvoiceDraft, normalizeInvoiceDraft, paymentTerms, validateInvoice, type InvoiceDraft } from './validation'

const CURRENCIES = ['CHF', 'EUR', 'USD', 'GBP']

export function InvoiceEditorForm({ invoice, customers, services, defaults, save, onCancel, onDirtyChange }: {
  invoice?: Invoice
  customers: Customer[]
  services: CatalogService[]
  defaults: { today: string; currency: string }
  save: (input: InvoiceInput) => Promise<void>
  onCancel: () => void
  onDirtyChange?: (dirty: boolean) => void
}) {
  const [draft, setDraft] = useState<InvoiceDraft>(() => invoice ? draftFromInvoice(invoice) : emptyInvoiceDraft(defaults.today, defaults.currency))
  const initial = useRef(JSON.stringify(normalizeInvoiceDraft(draft)))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const form = useRef<HTMLFormElement>(null)
  const ids = { customer: useId(), texts: useId() }
  const input = useMemo(() => normalizeInvoiceDraft(draft), [draft])
  const preview = useMemo(() => previewTotals(input.items, input.currency), [input])
  const due = dueDate(input)

  const customerOptions = useMemo(() => {
    const options = customers.filter(customer => !customer.archived).map(customer => ({ id: customer.id, label: `${customer.displayName} · ${customer.customerNumber}` }))
    if (invoice && !options.some(option => option.id === invoice.customerId)) options.unshift({ id: invoice.customerId, label: `${invoice.customer.name} · ${invoice.customer.customerNumber} (archiviert)` })
    return options
  }, [customers, invoice])
  const currency = input.currency
  const currencies = CURRENCIES.includes(currency) ? CURRENCIES : [...CURRENCIES, currency]
  const terms = paymentTerms.includes(Number(draft.paymentTermDays)) ? paymentTerms : [...paymentTerms, Number(draft.paymentTermDays)].sort((a, b) => a - b)

  function update(next: InvoiceDraft, clear: string[] = []) {
    setDraft(next)
    onDirtyChange?.(JSON.stringify(normalizeInvoiceDraft(next)) !== initial.current)
    if (clear.length) setErrors(previous => Object.fromEntries(Object.entries(previous).filter(([key]) => !clear.includes(key) && key !== 'items')))
  }
  function setField<K extends keyof Omit<InvoiceDraft, 'items'>>(key: K, value: InvoiceDraft[K]) { update({ ...draft, [key]: value }, [key]) }
  function setItem(index: number, key: keyof Omit<DraftItem, 'key' | 'serviceId'>, value: string) {
    update({ ...draft, items: draft.items.map((item, position) => position === index ? { ...item, [key]: value } : item) }, [`items[${index}].${key}`])
  }
  function setItems(items: DraftItem[], focus?: string) {
    update({ ...draft, items })
    setErrors(previous => Object.fromEntries(Object.entries(previous).filter(([key]) => !key.startsWith('items'))))
    if (focus) requestAnimationFrame(() => form.current?.querySelector<HTMLElement>(focus)?.focus())
  }
  function focusInvalid() { requestAnimationFrame(() => form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()) }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy.current) return
    const invalid = validateInvoice(input)
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
        setError('Die Rechnung wurde inzwischen ausgestellt und kann nicht mehr bearbeitet werden. Bitte lade sie neu.')
      } else {
        setError('Das Speichern konnte nicht bestätigt werden. Prüfe die Rechnungsliste, bevor du es erneut versuchst.')
      }
    } finally { busy.current = false; setPending(false) }
  }

  return <form ref={form} className="quote-editor" onSubmit={submit} noValidate aria-busy={pending}>
    <div className="quote-editor-main">
      {error && <p className="auth-error" role="alert">{error}</p>}
      <fieldset className="quote-card" disabled={pending} aria-labelledby={ids.customer}>
        <h2 id={ids.customer}>Kunde und Zahlungsbedingungen</h2>
        <FormField label="Kunde" error={errors.customerId} hint={customerOptions.length ? undefined : 'Lege zuerst einen Kunden an.'}>
          <Select name="customerId" required value={draft.customerId} onChange={event => setField('customerId', event.target.value)}>
            <option value="">Kunde auswählen…</option>
            {customerOptions.map(option => <option key={option.id} value={option.id}>{option.label}</option>)}
          </Select>
        </FormField>
        <div className="quote-grid quote-grid--3">
          <FormField label="Rechnungsdatum" error={errors.issueDate}><Input type="date" name="issueDate" required value={draft.issueDate} onChange={event => setField('issueDate', event.target.value)} /></FormField>
          <FormField label="Zahlungsfrist" error={errors.paymentTermDays} hint={due ? `Fällig am ${formatDate(due)}` : undefined}>
            <Select name="paymentTermDays" value={draft.paymentTermDays} onChange={event => setField('paymentTermDays', event.target.value)}>
              {terms.map(days => <option key={days} value={String(days)}>{days === 0 ? 'Sofort' : `${days} Tage`}</option>)}
            </Select>
          </FormField>
          <FormField label="Währung" error={errors.currency} hint={currency === 'CHF' || currency === 'EUR' ? undefined : 'QR-Zahlteil nur für CHF und EUR.'}>
            <Select name="currency" value={currency} onChange={event => setField('currency', event.target.value)}>{currencies.map(code => <option key={code} value={code}>{code}</option>)}</Select>
          </FormField>
        </div>
      </fieldset>

      <PositionsEditor items={draft.items} currency={currency} services={services} pending={pending} errors={errors}
        lines={preview.lines} addFreeId="invoice-add-free" onItems={setItems} onItem={setItem} />

      <fieldset className="quote-card" disabled={pending} aria-labelledby={ids.texts}>
        <h2 id={ids.texts}>Texte</h2>
        <FormField label="Einleitungstext (optional)" error={errors.introductionText}><Textarea name="introductionText" rows={3} value={draft.introductionText} placeholder="Vielen Dank für Ihren Auftrag. Wir erlauben uns, folgende Leistungen zu verrechnen:" onChange={event => setField('introductionText', event.target.value)} /></FormField>
        <FormField label="Abschlusstext (optional)" error={errors.closingText}><Textarea name="closingText" rows={3} value={draft.closingText} placeholder="Freundliche Grüsse" onChange={event => setField('closingText', event.target.value)} /></FormField>
        <FormField label="Interne Notizen (optional)" hint="Nur intern sichtbar, erscheint nicht im PDF." error={errors.notes}><Textarea name="notes" rows={2} value={draft.notes} onChange={event => setField('notes', event.target.value)} /></FormField>
      </fieldset>
    </div>

    <aside className="quote-editor-aside">
      <QuoteSummary currency={currency} subtotal={preview.subtotal} taxTotal={preview.taxTotal} grandTotal={preview.grandTotal} taxGroups={preview.taxGroups}
        note={preview.complete ? 'Vorschau. Die verbindlichen Beträge berechnet Hustivoo beim Speichern.' : 'Unvollständige Positionen sind noch nicht berücksichtigt.'}>
        <Button type="submit" disabled={pending}>{pending ? 'Wird gespeichert…' : invoice ? 'Änderungen speichern' : 'Entwurf speichern'}</Button>
        <Button variant="secondary" disabled={pending} onClick={onCancel}>Abbrechen</Button>
      </QuoteSummary>
    </aside>
  </form>
}
