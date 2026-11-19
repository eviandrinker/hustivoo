import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { useBusiness } from '@/business/context'
import * as api from '@/api/invoices'
import { listCustomers } from '@/api/customers'
import { listServices } from '@/api/services'
import { invoiceEditorStore } from '@/invoices/store'
import { InvoiceEditorForm } from '@/invoices/InvoiceEditorForm'
import { localToday } from '@/quotes/validation'
import { Button } from '@/components/ui/button'
import { ErrorState, InformationPanel, LoadingState, PageHeader } from '@/components/ui/display'

export function InvoiceEditorPage() {
  const { active } = useBusiness()
  const { invoiceId } = useParams()
  const [origin] = useState(() => active?.id)
  if (!active) return null
  if (active.id !== origin) return <Navigate to="/invoices" replace />
  return <InvoiceEditor key={`${active.id}:${invoiceId ?? 'new'}`} businessId={active.id} currency={active.currency} invoiceId={invoiceId} />
}

const editorApi = { getInvoice: api.getInvoice, listCustomers: (businessId: string) => listCustomers(businessId), listServices }

function InvoiceEditor({ businessId, currency, invoiceId }: { businessId: string; currency: string; invoiceId?: string }) {
  const navigate = useNavigate()
  const [store] = useState(() => invoiceEditorStore(businessId, invoiceId, editorApi))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [dirty, setDirty] = useState(false)
  const [today] = useState(() => localToday())
  const mounted = useRef(true)
  useEffect(() => {
    mounted.current = true
    void store.load()
    return () => { mounted.current = false; store.clear() }
  }, [store])
  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const back = invoiceId ? `/invoices/${encodeURIComponent(invoiceId)}` : '/invoices'
  function cancel() {
    if (dirty && !window.confirm('Ungespeicherte Änderungen verwerfen?')) return
    navigate(back)
  }
  const invoice = state.invoice
  const title = invoiceId ? (invoice ? `${invoice.invoiceNumber} bearbeiten` : 'Rechnung bearbeiten') : 'Neue Rechnung'
  return <div className="page-content quote-page">
    <Link className="text-link" to={back}>{invoiceId ? '← Zurück zur Rechnung' : '← Alle Rechnungen'}</Link>
    <PageHeader title={title} description={invoiceId ? 'Entwürfe kannst du bis zur Ausstellung anpassen.' : 'Wähle einen Kunden und stelle die Positionen zusammen. Die Nummer wird beim Speichern vergeben.'} />
    {state.status === 'loading' && <LoadingState label="Rechnungsdaten werden geladen…" />}
    {state.status === 'error' && <ErrorState title="Rechnungsdaten nicht verfügbar" description="Die Rechnung, die Kunden oder der Leistungskatalog konnten nicht geladen werden." action={<Button onClick={() => void store.load()}>Erneut laden</Button>} />}
    {state.status === 'ready' && invoice && invoice.status !== 'DRAFT' && <InformationPanel>
      Diese Rechnung wurde bereits ausgestellt und kann nicht mehr bearbeitet werden. <Link className="text-link" to={back}>Zur Rechnung</Link>
    </InformationPanel>}
    {state.status === 'ready' && (!invoice || invoice.status === 'DRAFT') && <InvoiceEditorForm
      invoice={invoice ?? undefined} customers={state.customers} services={state.services}
      defaults={{ today, currency }} onCancel={cancel} onDirtyChange={setDirty}
      save={async input => {
        const saved = await store.save(() => api.saveInvoice(businessId, input, invoiceId))
        if (!saved || !mounted.current) return
        setDirty(false)
        navigate(`/invoices/${encodeURIComponent(saved.id)}`, { replace: true, state: { message: invoiceId ? 'Änderungen gespeichert.' : `Entwurf ${saved.invoiceNumber} erstellt.` } })
      }} />}
  </div>
}
