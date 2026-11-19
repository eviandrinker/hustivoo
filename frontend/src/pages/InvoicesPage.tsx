import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { useBusiness } from '@/business/context'
import * as api from '@/api/invoices'
import { invoiceListStore } from '@/invoices/store'
import { InvoiceResults } from '@/invoices/InvoiceResults'
import { invoicePdfFilename, invoiceStatusLabels } from '@/invoices/labels'
import { saveFile } from '@/quotes/download'
import { Button } from '@/components/ui/button'
import { Input, Select } from '@/components/ui/forms'
import { PageHeader } from '@/components/ui/display'

export function InvoicesPage() {
  const { active } = useBusiness()
  return active ? <InvoiceWorkspace key={active.id} businessId={active.id} /> : null
}

function InvoiceWorkspace({ businessId }: { businessId: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [store] = useState(() => invoiceListStore(businessId, api))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [filters, setFilters] = useState<api.InvoiceFilters>({ ...api.defaultInvoiceFilters })
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

  async function download(invoice: api.InvoiceSummary) {
    if (downloading) return
    setDownloading(invoice.id); setError('')
    try {
      const blob = await api.downloadInvoicePdf(businessId, invoice.id)
      if (mounted.current) saveFile(blob, invoicePdfFilename(invoice.invoiceNumber))
    } catch {
      if (mounted.current) setError(`Das PDF für ${invoice.invoiceNumber} konnte nicht erstellt werden. Bitte versuche es erneut.`)
    } finally {
      if (mounted.current) setDownloading(null)
    }
  }

  return <div className="page-content quotes-page">
    <PageHeader title="Rechnungen" description="Stelle Rechnungen mit Schweizer QR-Zahlteil aus und behalte offene Beträge im Blick."
      meta={<Button id="create-invoice" onClick={() => navigate('/invoices/new')}><Plus size={16} aria-hidden="true" />Neue Rechnung</Button>} />
    <div className="customer-toolbar service-toolbar quote-toolbar">
      <label>Rechnungen suchen<Input type="search" placeholder="Nummer oder Kunde" maxLength={320} value={filters.search} onChange={event => setFilters(previous => ({ ...previous, search: event.target.value }))} /></label>
      <label>Status<Select value={filters.status} onChange={event => setFilters(previous => ({ ...previous, status: event.target.value as api.InvoiceFilters['status'] }))}>
        <option value="">Alle Status</option>
        {api.displayStatuses.map(status => <option key={status} value={status}>{invoiceStatusLabels[status]}</option>)}
      </Select></label>
    </div>
    {feedback && <p className="auth-success" role="status">{feedback}</p>}
    {error && <p className="auth-error" role="alert">{error}</p>}
    <InvoiceResults state={state} filters={filters} downloading={downloading}
      onReload={() => void store.load(filters)}
      onReset={() => setFilters({ ...api.defaultInvoiceFilters })}
      onNew={() => navigate('/invoices/new')}
      onOpen={invoice => navigate(`/invoices/${encodeURIComponent(invoice.id)}`)}
      onEdit={invoice => navigate(`/invoices/${encodeURIComponent(invoice.id)}/edit`)}
      onDownload={invoice => void download(invoice)} />
  </div>
}
