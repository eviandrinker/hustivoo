import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useBusiness } from '@/business/context'
import * as api from '@/api/quotes'
import { ApiError } from '@/api/client'
import { quoteDetailStore } from '@/quotes/store'
import { QuoteActions, QuoteOverview } from '@/quotes/QuoteOverview'
import { QuoteStatusBadge } from '@/quotes/QuoteParts'
import { actionCopy, isExpired } from '@/quotes/labels'
import { formatMoney } from '@/quotes/money'
import { localToday, sendBlocker } from '@/quotes/validation'
import { pdfFilename, saveFile } from '@/quotes/download'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { ErrorState, LoadingState, PageHeader } from '@/components/ui/display'

export function QuoteDetailPage() {
  const { active } = useBusiness()
  const { quoteId } = useParams()
  // Quotes belong to one business: switching business leaves the record instead of showing a 404.
  const [origin] = useState(() => active?.id)
  if (!active || !quoteId) return null
  if (active.id !== origin) return <Navigate to="/quotes" replace />
  return <QuoteDetail key={`${active.id}:${quoteId}`} businessId={active.id} quoteId={quoteId} />
}

function QuoteDetail({ businessId, quoteId }: { businessId: string; quoteId: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [store] = useState(() => quoteDetailStore(businessId, quoteId, api))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [confirm, setConfirm] = useState<api.QuoteAction | 'delete' | null>(null)
  const [pending, setPending] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(() => typeof (location.state as { message?: unknown } | null)?.message === 'string' ? (location.state as { message: string }).message : '')
  const mounted = useRef(true)
  const busy = useRef(false)
  useEffect(() => {
    mounted.current = true
    void store.load()
    return () => { mounted.current = false; store.clear() }
  }, [store])

  async function run(operation: () => Promise<void>, failure: (error: unknown) => string) {
    if (busy.current) return
    busy.current = true; setPending(true); setError(''); setSuccess('')
    try { await operation() }
    catch (caught) { if (mounted.current) setError(failure(caught)) }
    finally { busy.current = false; if (mounted.current) setPending(false) }
  }
  const conflict = (caught: unknown) => caught instanceof ApiError && caught.status === 409
    ? 'Der Status wurde inzwischen geändert. Bitte lade das Angebot neu.'
    : 'Die Änderung konnte nicht bestätigt werden. Bitte lade das Angebot neu, bevor du es erneut versuchst.'

  function transition(action: api.QuoteAction) {
    void run(async () => {
      const applied = await store.apply(() => api.changeQuoteStatus(businessId, quoteId, action))
      if (applied && mounted.current) { setConfirm(null); setSuccess(actionCopy[action].done) }
    }, conflict)
  }
  function remove() {
    void run(async () => {
      const applied = await store.run(() => api.deleteQuote(businessId, quoteId))
      if (applied && mounted.current) navigate('/quotes', { replace: true, state: { message: 'Entwurf gelöscht.' } })
    }, conflict)
  }
  async function download(quote: api.Quote) {
    if (downloading) return
    setDownloading(true); setError('')
    try {
      const blob = await api.downloadQuotePdf(businessId, quote.id)
      if (mounted.current) saveFile(blob, pdfFilename(quote.quoteNumber))
    } catch {
      if (mounted.current) setError('Das PDF konnte nicht erstellt werden. Bitte versuche es erneut.')
    } finally {
      if (mounted.current) setDownloading(false)
    }
  }

  const quote = state.quote
  const expired = quote ? isExpired(quote.status, quote.validUntil, localToday()) : false
  return <div className="page-content quote-page">
    <Link className="text-link" to="/quotes">← Alle Angebote</Link>
    {state.status === 'loading' && <LoadingState label="Angebot wird geladen…" />}
    {state.status === 'error' && <ErrorState title="Angebot nicht verfügbar" description="Das Angebot existiert nicht in diesem Unternehmen oder die Verbindung wurde unterbrochen." action={<Button onClick={() => void store.load()}>Erneut laden</Button>} />}
    {quote && <>
      <PageHeader title={quote.quoteNumber} description={`${quote.customer.name} · ${formatMoney(quote.grandTotal, quote.currency)}`}
        meta={<QuoteActions quote={quote} pending={pending} downloading={downloading}
          onEdit={() => navigate(`/quotes/${encodeURIComponent(quote.id)}/edit`)}
          onDelete={() => { setError(''); setConfirm('delete') }}
          onDownload={() => void download(quote)}
          onAction={action => {
            const blocker = action === 'send' ? sendBlocker(quote) : null
            setSuccess('')
            if (blocker) setError(blocker); else { setError(''); setConfirm(action) }
          }} />} />
      <div className="quote-status-row"><QuoteStatusBadge status={quote.status} expired={expired} /></div>
      {success && <p className="auth-success" role="status">{success}</p>}
      {error && !confirm && <ErrorState title="Aktion nicht möglich" description={error} action={<Button variant="secondary" onClick={() => { setError(''); void store.load() }}>Neu laden</Button>} />}
      <QuoteOverview quote={quote} expired={expired} />
      {confirm && <Dialog open alert iconClose closeDisabled={pending}
        title={confirm === 'delete' ? 'Entwurf löschen?' : actionCopy[confirm].title}
        description={confirm === 'delete' ? `${quote.quoteNumber} wird dauerhaft gelöscht. Die Nummer wird nicht erneut vergeben.` : actionCopy[confirm].description}
        onClose={() => { if (!pending) { setConfirm(null); setError('') } }}
        footer={<>
          <Button variant="secondary" disabled={pending} onClick={() => { setConfirm(null); setError('') }}>Abbrechen</Button>
          <Button variant={confirm === 'delete' ? 'destructive' : 'primary'} disabled={pending} onClick={() => confirm === 'delete' ? remove() : transition(confirm)}>
            {pending ? 'Wird ausgeführt…' : confirm === 'delete' ? 'Endgültig löschen' : actionCopy[confirm].confirm}
          </Button>
        </>}>{error && <p className="field-error" role="alert">{error}</p>}</Dialog>}
    </>}
  </div>
}
