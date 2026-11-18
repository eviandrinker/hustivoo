import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { useBusiness } from '@/business/context'
import * as api from '@/api/quotes'
import { listCustomers } from '@/api/customers'
import { listServices } from '@/api/services'
import { quoteEditorStore } from '@/quotes/store'
import { QuoteEditorForm } from '@/quotes/QuoteEditorForm'
import { localToday } from '@/quotes/validation'
import { Button } from '@/components/ui/button'
import { ErrorState, InformationPanel, LoadingState, PageHeader } from '@/components/ui/display'

export function QuoteEditorPage() {
  const { active } = useBusiness()
  const { quoteId } = useParams()
  const [origin] = useState(() => active?.id)
  if (!active) return null
  if (active.id !== origin) return <Navigate to="/quotes" replace />
  return <QuoteEditor key={`${active.id}:${quoteId ?? 'new'}`} businessId={active.id} currency={active.currency} quoteId={quoteId} />
}

const editorApi = { getQuote: api.getQuote, listCustomers: (businessId: string) => listCustomers(businessId), listServices }

function QuoteEditor({ businessId, currency, quoteId }: { businessId: string; currency: string; quoteId?: string }) {
  const navigate = useNavigate()
  const [store] = useState(() => quoteEditorStore(businessId, quoteId, editorApi))
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

  const back = quoteId ? `/quotes/${encodeURIComponent(quoteId)}` : '/quotes'
  function cancel() {
    if (dirty && !window.confirm('Ungespeicherte Änderungen verwerfen?')) return
    navigate(back)
  }
  const quote = state.quote
  const title = quoteId ? (quote ? `${quote.quoteNumber} bearbeiten` : 'Angebot bearbeiten') : 'Neues Angebot'
  return <div className="page-content quote-page">
    <Link className="text-link" to={back}>{quoteId ? '← Zurück zum Angebot' : '← Alle Angebote'}</Link>
    <PageHeader title={title} description={quoteId ? 'Entwürfe kannst du bis zum Versand beliebig anpassen.' : 'Wähle einen Kunden und stelle die Positionen zusammen. Die Nummer wird beim Speichern vergeben.'} />
    {state.status === 'loading' && <LoadingState label="Angebotsdaten werden geladen…" />}
    {state.status === 'error' && <ErrorState title="Angebotsdaten nicht verfügbar" description="Das Angebot, die Kunden oder der Leistungskatalog konnten nicht geladen werden." action={<Button onClick={() => void store.load()}>Erneut laden</Button>} />}
    {state.status === 'ready' && quote && quote.status !== 'DRAFT' && <InformationPanel>
      Dieses Angebot wurde bereits versendet und kann nicht mehr bearbeitet werden. <Link className="text-link" to={back}>Zum Angebot</Link>
    </InformationPanel>}
    {state.status === 'ready' && (!quote || quote.status === 'DRAFT') && <QuoteEditorForm
      quote={quote ?? undefined} customers={state.customers} services={state.services}
      defaults={{ today, currency }} onCancel={cancel} onDirtyChange={setDirty}
      save={async input => {
        const saved = await store.save(() => api.saveQuote(businessId, input, quoteId))
        if (!saved || !mounted.current) return
        setDirty(false)
        navigate(`/quotes/${encodeURIComponent(saved.id)}`, { replace: true, state: { message: quoteId ? 'Änderungen gespeichert.' : `Entwurf ${saved.quoteNumber} erstellt.` } })
      }} />}
  </div>
}
