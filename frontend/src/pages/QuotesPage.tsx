import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { useBusiness } from '@/business/context'
import * as api from '@/api/quotes'
import { quoteListStore } from '@/quotes/store'
import { QuoteResults } from '@/quotes/QuoteResults'
import { quoteStatusLabels } from '@/quotes/labels'
import { localToday } from '@/quotes/validation'
import { pdfFilename, saveFile } from '@/quotes/download'
import { Button } from '@/components/ui/button'
import { Input, Select } from '@/components/ui/forms'
import { PageHeader } from '@/components/ui/display'

export function QuotesPage() {
  const { active } = useBusiness()
  return active ? <QuoteWorkspace key={active.id} businessId={active.id} /> : null
}

function QuoteWorkspace({ businessId }: { businessId: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [store] = useState(() => quoteListStore(businessId, api))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [filters, setFilters] = useState<api.QuoteFilters>({ ...api.defaultQuoteFilters })
  const [downloading, setDownloading] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [feedback] = useState(() => typeof (location.state as { message?: unknown } | null)?.message === 'string' ? (location.state as { message: string }).message : '')
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; store.clear() }
  }, [store])
  useEffect(() => {
    const timeout = setTimeout(() => { void store.load(filters) }, 200)
    return () => clearTimeout(timeout)
  }, [store, filters])

  async function download(quote: api.QuoteSummary) {
    if (downloading) return
    setDownloading(quote.id); setError('')
    try {
      const blob = await api.downloadQuotePdf(businessId, quote.id)
      // A late response after a business switch or logout is discarded with the unmounted page.
      if (mounted.current) saveFile(blob, pdfFilename(quote.quoteNumber))
    } catch {
      if (mounted.current) setError(`Das PDF für ${quote.quoteNumber} konnte nicht erstellt werden. Bitte versuche es erneut.`)
    } finally {
      if (mounted.current) setDownloading(null)
    }
  }

  return <div className="page-content quotes-page">
    <PageHeader title="Angebote" description="Erstelle Angebote aus deinem Leistungskatalog und verfolge, welche angenommen werden."
      meta={<Button id="create-quote" onClick={() => navigate('/quotes/new')}><Plus size={16} aria-hidden="true" />Angebot erstellen</Button>} />
    <div className="customer-toolbar service-toolbar quote-toolbar">
      <label>Angebote suchen<Input type="search" placeholder="Nummer oder Kunde" maxLength={320} value={filters.search} onChange={event => setFilters(previous => ({ ...previous, search: event.target.value }))} /></label>
      <label>Status<Select value={filters.status} onChange={event => setFilters(previous => ({ ...previous, status: event.target.value as api.QuoteFilters['status'] }))}>
        <option value="">Alle Status</option>
        {api.quoteStatuses.map(status => <option key={status} value={status}>{quoteStatusLabels[status]}</option>)}
      </Select></label>
    </div>
    {feedback && <p className="auth-success" role="status">{feedback}</p>}
    {error && <p className="auth-error" role="alert">{error}</p>}
    <QuoteResults state={state} filters={filters} today={localToday()} downloading={downloading}
      onReload={() => void store.load(filters)}
      onReset={() => setFilters({ ...api.defaultQuoteFilters })}
      onNew={() => navigate('/quotes/new')}
      onOpen={quote => navigate(`/quotes/${encodeURIComponent(quote.id)}`)}
      onEdit={quote => navigate(`/quotes/${encodeURIComponent(quote.id)}/edit`)}
      onDownload={quote => void download(quote)} />
  </div>
}
