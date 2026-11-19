import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useBusiness } from '@/business/context'
import * as api from '@/api/invoices'
import { ApiError } from '@/api/client'
import { invoiceDetailStore } from '@/invoices/store'
import { InvoiceActions, InvoiceOverview, PaymentForm, ReasonForm, type InvoiceCommand } from '@/invoices/InvoiceOverview'
import { InvoiceStatusBadge } from '@/invoices/InvoiceParts'
import { invoicePdfFilename, issueBlocker } from '@/invoices/labels'
import { formatMoney } from '@/quotes/money'
import { localToday } from '@/quotes/validation'
import { saveFile } from '@/quotes/download'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { ErrorState, LoadingState, PageHeader } from '@/components/ui/display'

export function InvoiceDetailPage() {
  const { active } = useBusiness()
  const { invoiceId } = useParams()
  // Invoices belong to one business: switching business leaves the record instead of showing a 404.
  const [origin] = useState(() => active?.id)
  if (!active || !invoiceId) return null
  if (active.id !== origin) return <Navigate to="/invoices" replace />
  return <InvoiceDetail key={`${active.id}:${invoiceId}`} businessId={active.id} invoiceId={invoiceId} />
}

type Dialogs = { kind: 'issue' | 'delete' | 'pay' | 'cancel' } | { kind: 'reverse'; payment: api.InvoicePayment } | null

function InvoiceDetail({ businessId, invoiceId }: { businessId: string; invoiceId: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [store] = useState(() => invoiceDetailStore(businessId, invoiceId, api))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [dialog, setDialog] = useState<Dialogs>(null)
  const [pending, setPending] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(() => typeof (location.state as { message?: unknown } | null)?.message === 'string' ? (location.state as { message: string }).message : '')
  const [today] = useState(() => localToday())
  const mounted = useRef(true)
  const busy = useRef(false)
  useEffect(() => {
    mounted.current = true
    void store.load()
    return () => { mounted.current = false; store.clear() }
  }, [store])

  async function run(operation: () => Promise<void>) {
    if (busy.current) return
    busy.current = true; setPending(true); setError(''); setSuccess('')
    try { await operation() }
    catch (caught) {
      if (mounted.current) setError(caught instanceof ApiError && caught.status === 409
        ? 'Der Status wurde inzwischen geändert. Bitte lade die Rechnung neu.'
        : 'Die Änderung konnte nicht bestätigt werden. Bitte lade die Rechnung neu, bevor du es erneut versuchst.')
    } finally { busy.current = false; if (mounted.current) setPending(false) }
  }
  /** For dialogs with their own forms: errors are shown inside the dialog, so they are re-thrown. */
  async function apply(operation: () => Promise<api.Invoice>, message: string) {
    const applied = await store.apply(operation)
    if (applied && mounted.current) { setDialog(null); setSuccess(message) }
  }
  async function download(invoice: api.Invoice) {
    if (downloading) return
    setDownloading(true); setError('')
    try {
      const blob = await api.downloadInvoicePdf(businessId, invoice.id)
      if (mounted.current) saveFile(blob, invoicePdfFilename(invoice.invoiceNumber))
    } catch {
      if (mounted.current) setError('Das PDF konnte nicht erstellt werden. Bitte versuche es erneut.')
    } finally { if (mounted.current) setDownloading(false) }
  }

  const invoice = state.invoice
  function command(next: InvoiceCommand) {
    if (!invoice) return
    setSuccess(''); setError('')
    if (next === 'edit') navigate(`/invoices/${encodeURIComponent(invoice.id)}/edit`)
    else if (next === 'download') void download(invoice)
    else if (next === 'issue') { const blocker = issueBlocker(invoice); if (blocker) setError(blocker); else setDialog({ kind: 'issue' }) }
    else setDialog({ kind: next })
  }
  const close = () => { if (!pending) { setDialog(null); setError('') } }

  return <div className="page-content quote-page">
    <Link className="text-link" to="/invoices">← Alle Rechnungen</Link>
    {state.status === 'loading' && <LoadingState label="Rechnung wird geladen…" />}
    {state.status === 'error' && <ErrorState title="Rechnung nicht verfügbar" description="Die Rechnung existiert nicht in diesem Unternehmen oder die Verbindung wurde unterbrochen." action={<Button onClick={() => void store.load()}>Erneut laden</Button>} />}
    {invoice && <>
      <PageHeader title={invoice.invoiceNumber} description={`${invoice.customer.name} · ${formatMoney(invoice.grandTotal, invoice.currency)}`}
        meta={<InvoiceActions invoice={invoice} pending={pending} downloading={downloading} onCommand={command} />} />
      <div className="quote-status-row"><InvoiceStatusBadge status={invoice.displayStatus} /></div>
      {success && <p className="auth-success" role="status">{success}</p>}
      {error && !dialog && <ErrorState title="Aktion nicht möglich" description={error} action={<Button variant="secondary" onClick={() => { setError(''); void store.load() }}>Neu laden</Button>} />}
      <InvoiceOverview invoice={invoice} pending={pending} onReverse={payment => { setSuccess(''); setDialog({ kind: 'reverse', payment }) }} />

      {(dialog?.kind === 'issue' || dialog?.kind === 'delete') && <Dialog open alert iconClose closeDisabled={pending}
        title={dialog.kind === 'issue' ? 'Rechnung ausstellen?' : 'Entwurf löschen?'}
        description={dialog.kind === 'issue'
          ? `${invoice.invoiceNumber} wird mit Kunden- und Absenderdaten fixiert und kann danach nicht mehr bearbeitet oder gelöscht werden.${invoice.qrBill.available ? '' : ' Das PDF enthält keinen QR-Zahlteil.'}`
          : `${invoice.invoiceNumber} wird dauerhaft gelöscht. Die Nummer wird nicht erneut vergeben.`}
        onClose={close}
        footer={<>
          <Button variant="secondary" disabled={pending} onClick={close}>Abbrechen</Button>
          <Button variant={dialog.kind === 'delete' ? 'destructive' : 'primary'} disabled={pending} onClick={() => void run(async () => {
            if (dialog.kind === 'issue') await apply(() => api.issueInvoice(businessId, invoiceId), 'Rechnung ausgestellt.')
            else if (await store.run(() => api.deleteInvoice(businessId, invoiceId)) && mounted.current) navigate('/invoices', { replace: true, state: { message: 'Entwurf gelöscht.' } })
          })}>{pending ? 'Wird ausgeführt…' : dialog.kind === 'issue' ? 'Ausstellen' : 'Endgültig löschen'}</Button>
        </>}>{error && <p className="field-error" role="alert">{error}</p>}</Dialog>}

      {dialog?.kind === 'pay' && <Dialog open iconClose closeDisabled={pending} title="Zahlung erfassen" description={`Zahlungseingang für ${invoice.invoiceNumber}. Teilzahlungen sind möglich.`} onClose={close}>
        <PaymentForm invoice={invoice} today={today} onCancel={close}
          save={input => apply(() => api.recordPayment(businessId, invoiceId, input), 'Zahlung gebucht.')} />
      </Dialog>}

      {dialog?.kind === 'cancel' && <Dialog open iconClose closeDisabled={pending} title="Rechnung stornieren?" description={`${invoice.invoiceNumber} bleibt mit allen Angaben erhalten, wird aber als storniert markiert und nicht mehr eingefordert.`} onClose={close}>
        <ReasonForm label="Grund der Stornierung" confirm="Stornieren" destructive onCancel={close}
          save={reason => apply(() => api.cancelInvoice(businessId, invoiceId, reason), 'Rechnung storniert.')} />
      </Dialog>}

      {dialog?.kind === 'reverse' && <Dialog open iconClose closeDisabled={pending} title="Zahlung stornieren?" description={`Die Zahlung über ${formatMoney(dialog.payment.amount, invoice.currency)} bleibt in der Historie sichtbar und wird als storniert markiert. Erfasse danach bei Bedarf die korrekte Zahlung.`} onClose={close}>
        <ReasonForm label="Grund der Stornobuchung" confirm="Zahlung stornieren" destructive onCancel={close}
          save={reason => apply(() => api.reversePayment(businessId, invoiceId, dialog.payment.id, reason), 'Zahlung storniert.')} />
      </Dialog>}
    </>}
  </div>
}
