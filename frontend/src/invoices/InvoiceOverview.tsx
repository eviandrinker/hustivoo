import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { Ban, Banknote, Download, Pencil, Send, Trash2, Undo2 } from 'lucide-react'
import { paymentMethods, type Invoice, type InvoicePayment, type PaymentInput } from '@/api/invoices'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { DataTable } from '@/components/ui/data-table'
import { InformationPanel } from '@/components/ui/display'
import { FormField, Input, Select, Textarea } from '@/components/ui/forms'
import { unitLabels } from '@/services/validation'
import { QuoteSummary } from '@/quotes/QuoteParts'
import { formatDate, formatMoney, formatQuantity, formatRate } from '@/quotes/money'
import { invoiceCapabilities, paymentMethodLabels } from './labels'
import { emptyPayment, normalizePayment, validatePayment, validateReason, type PaymentDraft } from './validation'

export type InvoiceCommand = 'edit' | 'delete' | 'issue' | 'pay' | 'cancel' | 'download'

/** Status-dependent actions; the server enforces the same rules. */
export function InvoiceActions({ invoice, pending, downloading, onCommand }: {
  invoice: Invoice; pending: boolean; downloading: boolean; onCommand: (command: InvoiceCommand) => void
}) {
  const can = invoiceCapabilities(invoice)
  return <div className="page-actions quote-actions">
    {can.edit && <Button variant="secondary" disabled={pending} onClick={() => onCommand('edit')}><Pencil size={15} aria-hidden="true" />Bearbeiten</Button>}
    <Button variant="secondary" disabled={pending || downloading} onClick={() => onCommand('download')}><Download size={15} aria-hidden="true" />{downloading ? 'PDF wird erstellt…' : 'PDF'}</Button>
    {can.issue && <Button disabled={pending} onClick={() => onCommand('issue')}><Send size={15} aria-hidden="true" />Rechnung ausstellen</Button>}
    {can.pay && <Button disabled={pending} onClick={() => onCommand('pay')}><Banknote size={15} aria-hidden="true" />Zahlung erfassen</Button>}
    {can.cancel && <Button variant="tertiary" className="quote-delete" disabled={pending} onClick={() => onCommand('cancel')}><Ban size={15} aria-hidden="true" />Stornieren</Button>}
    {can.remove && <Button variant="tertiary" className="quote-delete" disabled={pending} onClick={() => onCommand('delete')}><Trash2 size={15} aria-hidden="true" />Löschen</Button>}
  </div>
}

function time(value: string) {
  return new Intl.DateTimeFormat('de-CH', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function statusNote(invoice: Invoice) {
  switch (invoice.status) {
    case 'DRAFT': return null
    case 'CANCELLED': return `Diese Rechnung wurde storniert${invoice.cancellationReason ? `: «${invoice.cancellationReason}»` : '.'} Sie bleibt unverändert erhalten.`
    case 'PAID': return 'Diese Rechnung ist vollständig bezahlt. Inhalt und Beträge bleiben unverändert.'
    default: return invoice.overdue
      ? `Die Zahlungsfrist ist am ${formatDate(invoice.dueDate)} abgelaufen. Offen: ${formatMoney(invoice.openAmount, invoice.currency)}.`
      : 'Ausgestellte Rechnungen sind schreibgeschützt. Zahlungen erfasst du unten; Korrekturen erfolgen über Stornobuchungen.'
  }
}

export function InvoiceOverview({ invoice, pending, onReverse }: { invoice: Invoice; pending: boolean; onReverse: (payment: InvoicePayment) => void }) {
  const customer = invoice.customer
  const address = [customer.companyName !== customer.name ? customer.companyName : null, customer.contactName !== customer.name ? customer.contactName : null,
    customer.street, [customer.postalCode, customer.city].filter(Boolean).join(' '), customer.country].filter(Boolean)
  const note = statusNote(invoice)
  const can = invoiceCapabilities(invoice)
  return <div className="quote-detail-layout">
    <div className="quote-editor-main">
      {note && <InformationPanel className={invoice.overdue ? 'quote-readonly invoice-overdue-panel' : 'quote-readonly'}>{note}</InformationPanel>}
      <QrBillNotice invoice={invoice} />
      <section className="quote-card quote-parties" aria-label="Kunde und Konditionen">
        <div>
          <h2>Kunde</h2>
          <p className="quote-party-name">{customer.name}</p>
          {address.map(line => <p key={line}>{line}</p>)}
          {customer.email && <p className="quote-muted">{customer.email}</p>}
        </div>
        <dl className="quote-meta">
          <div><dt>Kundennummer</dt><dd>{customer.customerNumber}</dd></div>
          <div><dt>Rechnungsdatum</dt><dd>{formatDate(invoice.issueDate)}</dd></div>
          <div><dt>Zahlungsfrist</dt><dd>{invoice.paymentTermDays === 0 ? 'sofort' : `${invoice.paymentTermDays} Tage`}</dd></div>
          <div><dt>Fällig am</dt><dd className={invoice.overdue ? 'invoice-overdue' : undefined}>{formatDate(invoice.dueDate)}</dd></div>
          <div><dt>Währung</dt><dd>{invoice.currency}</dd></div>
          {invoice.quote && <div><dt>Angebot</dt><dd><Link className="text-link" to={`/quotes/${encodeURIComponent(invoice.quote.id)}`}>{invoice.quote.quoteNumber}</Link></dd></div>}
          {invoice.issuedAt && <div><dt>Ausgestellt</dt><dd>{time(invoice.issuedAt)}</dd></div>}
          {invoice.cancelledAt && <div><dt>Storniert</dt><dd>{time(invoice.cancelledAt)}</dd></div>}
        </dl>
      </section>
      <section className="quote-card" aria-label="Positionen">
        {invoice.introductionText && <p className="quote-text">{invoice.introductionText}</p>}
        {invoice.items.length ? <DataTable caption={`Positionen von ${invoice.invoiceNumber}`} rows={invoice.items} rowKey={item => item.id} columns={[
          { key: 'position', header: 'Pos.', render: item => item.position },
          { key: 'description', header: 'Beschreibung', render: item => { const [title, ...rest] = item.description.split('\n'); return <div className="quote-line-description"><strong>{title}</strong>{rest.length > 0 && <span>{rest.join('\n')}</span>}</div> } },
          { key: 'quantity', header: 'Menge', numeric: true, render: item => `${formatQuantity(item.quantity)} ${unitLabels[item.unit]}` },
          { key: 'price', header: 'Einzelpreis', numeric: true, render: item => formatMoney(item.unitPrice, invoice.currency) },
          { key: 'tax', header: 'MWST', numeric: true, render: item => formatRate(item.taxRate) },
          { key: 'total', header: 'Betrag', numeric: true, render: item => formatMoney(item.lineSubtotal, invoice.currency) },
        ]} /> : <p className="quote-empty-items">Diese Rechnung enthält noch keine Positionen.</p>}
        {invoice.closingText && <p className="quote-text">{invoice.closingText}</p>}
      </section>
      {invoice.status !== 'DRAFT' && <PaymentHistory invoice={invoice} pending={pending} canReverse={can.reverse} onReverse={onReverse} />}
      {invoice.notes && <InformationPanel className="quote-notes"><strong>Interne Notiz</strong><p>{invoice.notes}</p></InformationPanel>}
    </div>
    <aside className="quote-editor-aside">
      <QuoteSummary currency={invoice.currency} subtotal={invoice.subtotal} taxTotal={invoice.taxTotal} grandTotal={invoice.grandTotal} taxGroups={invoice.taxGroups}>
        {invoice.status !== 'DRAFT' && invoice.status !== 'CANCELLED' && <dl className="invoice-balance">
          <div><dt>Bezahlt</dt><dd>{formatMoney(invoice.paidTotal, invoice.currency)}</dd></div>
          <div className={invoice.overdue ? 'invoice-open invoice-overdue' : 'invoice-open'}><dt>Offen</dt><dd>{formatMoney(invoice.openAmount, invoice.currency)}</dd></div>
        </dl>}
      </QuoteSummary>
    </aside>
  </div>
}

/** Explains whether the PDF carries a Swiss QR payment part and, if not, what is missing. */
export function QrBillNotice({ invoice }: { invoice: Invoice }) {
  if (invoice.status === 'CANCELLED') return null
  if (invoice.qrBill.available) {
    return invoice.status === 'DRAFT'
      ? <p className="invoice-qr-note">Das PDF enthält einen QR-Zahlteil als Vorschau mit «NICHT ZUR ZAHLUNG VERWENDEN». Beim Ausstellen wird er zahlbar.</p>
      : <p className="invoice-qr-note">Das PDF enthält einen Schweizer QR-Zahlteil über {formatMoney(invoice.grandTotal, invoice.currency)}.</p>
  }
  return <InformationPanel className="invoice-qr-missing" role="note">
    <strong>Ohne QR-Zahlteil</strong>
    <ul>{invoice.qrBill.problems.map(problem => <li key={problem}>{problem}</li>)}</ul>
    {invoice.status === 'DRAFT' && <p>Ergänze die Angaben in den <Link className="text-link" to="/settings">Einstellungen</Link>, bevor du die Rechnung ausstellst.</p>}
  </InformationPanel>
}

export function PaymentHistory({ invoice, pending, canReverse, onReverse }: { invoice: Invoice; pending: boolean; canReverse: boolean; onReverse: (payment: InvoicePayment) => void }) {
  const id = useId()
  return <section className="quote-card" aria-labelledby={id}>
    <div className="quote-card-heading"><h2 id={id}>Zahlungen</h2><span>{formatMoney(invoice.paidTotal, invoice.currency)} von {formatMoney(invoice.grandTotal, invoice.currency)}</span></div>
    {invoice.payments.length ? <ul className="invoice-payments">{invoice.payments.map(payment => <li key={payment.id} className={payment.reversed ? 'invoice-payment is-reversed' : 'invoice-payment'}>
      <div>
        <strong>{formatMoney(payment.amount, invoice.currency)}</strong>
        <span>{formatDate(payment.paymentDate)} · {paymentMethodLabels[payment.method]}{payment.reference ? ` · ${payment.reference}` : ''}</span>
        {payment.note && <span className="quote-muted">{payment.note}</span>}
        {payment.reversed && <span className="invoice-reversal">Storniert{payment.reversedAt ? ` am ${time(payment.reversedAt)}` : ''}: {payment.reversalReason}</span>}
      </div>
      {!payment.reversed && canReverse && <Button variant="tertiary" disabled={pending} aria-label={`Zahlung über ${formatMoney(payment.amount, invoice.currency)} vom ${formatDate(payment.paymentDate)} stornieren`} onClick={() => onReverse(payment)}>
        <Undo2 size={15} aria-hidden="true" />Stornieren
      </Button>}
    </li>)}</ul> : <p className="quote-empty-items">Noch keine Zahlungen erfasst.</p>}
  </section>
}

/** Manual payment entry; amount is pre-filled with the open amount and never exceeds it. */
export function PaymentForm({ invoice, today, save, onCancel }: { invoice: Invoice; today: string; save: (input: PaymentInput) => Promise<void>; onCancel: () => void }) {
  const [draft, setDraft] = useState<PaymentDraft>(() => emptyPayment(today, invoice.openAmount))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const set = (key: keyof PaymentDraft, value: string) => { setDraft(previous => ({ ...previous, [key]: value })); setErrors(previous => ({ ...previous, [key]: '' })) }
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    const input = normalizePayment(draft)
    const invalid = Object.fromEntries(Object.entries(validatePayment(input, invoice.openAmount, invoice.currency, today)).filter(([, value]) => value))
    setErrors(invalid); setError('')
    if (Object.keys(invalid).length) return
    setPending(true)
    try { await save(input) }
    catch (failure) {
      if (failure instanceof ApiError && failure.status === 400) { setErrors({ ...failure.fields, ...(failure.fields.amount ? { amount: 'Der Betrag ist ungültig oder höher als der offene Betrag.' } : {}) }); setError('Bitte prüfe die markierten Felder.') }
      else if (failure instanceof ApiError && failure.status === 409) setError('Die Rechnung nimmt keine Zahlungen mehr an. Bitte lade sie neu.')
      else setError('Die Zahlung konnte nicht bestätigt werden. Prüfe die Zahlungshistorie, bevor du sie erneut erfasst.')
    } finally { setPending(false) }
  }
  return <form className="invoice-payment-form" onSubmit={submit} noValidate>
    {error && <p className="auth-error" role="alert">{error}</p>}
    <p className="quote-muted">Offen: <strong>{formatMoney(invoice.openAmount, invoice.currency)}</strong></p>
    <div className="quote-grid quote-grid--2">
      <FormField label="Zahlungsdatum" error={errors.paymentDate}><Input type="date" name="paymentDate" max={today} value={draft.paymentDate} onChange={event => set('paymentDate', event.target.value)} /></FormField>
      <FormField label={`Betrag (${invoice.currency})`} error={errors.amount}><Input name="amount" inputMode="decimal" value={draft.amount} onChange={event => set('amount', event.target.value)} /></FormField>
    </div>
    <FormField label="Zahlungsart" error={errors.method}>
      <Select name="method" value={draft.method} onChange={event => set('method', event.target.value)}>
        {paymentMethods.map(method => <option key={method} value={method}>{paymentMethodLabels[method]}</option>)}
      </Select>
    </FormField>
    <FormField label="Referenz (optional)" hint="z. B. Kontoauszug oder Belegnummer" error={errors.reference}><Input name="reference" maxLength={160} value={draft.reference} onChange={event => set('reference', event.target.value)} /></FormField>
    <FormField label="Interne Bemerkung (optional)" error={errors.note}><Textarea name="note" rows={2} value={draft.note} onChange={event => set('note', event.target.value)} /></FormField>
    <div className="invoice-form-actions">
      <Button variant="secondary" disabled={pending} onClick={onCancel}>Abbrechen</Button>
      <Button type="submit" disabled={pending}>{pending ? 'Wird gebucht…' : 'Zahlung buchen'}</Button>
    </div>
  </form>
}

/** Cancellations and payment reversals always need a reason, which stays visible in the history. */
export function ReasonForm({ label, confirm, destructive = false, save, onCancel }: { label: string; confirm: string; destructive?: boolean; save: (reason: string) => Promise<void>; onCancel: () => void }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    const problem = validateReason(reason)
    if (problem) { setError(problem); return }
    setPending(true); setError('')
    try { await save(reason.trim()) }
    catch (failure) {
      setError(failure instanceof ApiError && failure.status === 409
        ? 'Das ist im aktuellen Zustand nicht möglich. Bitte lade die Rechnung neu.'
        : 'Die Änderung konnte nicht bestätigt werden. Bitte lade die Rechnung neu, bevor du es erneut versuchst.')
    } finally { setPending(false) }
  }
  return <form className="invoice-payment-form" onSubmit={submit} noValidate>
    <FormField label={label} error={error}><Textarea name="reason" rows={3} maxLength={520} value={reason} onChange={event => { setReason(event.target.value); setError('') }} /></FormField>
    <div className="invoice-form-actions">
      <Button variant="secondary" disabled={pending} onClick={onCancel}>Abbrechen</Button>
      <Button type="submit" variant={destructive ? 'destructive' : 'primary'} disabled={pending}>{pending ? 'Wird ausgeführt…' : confirm}</Button>
    </div>
  </form>
}
