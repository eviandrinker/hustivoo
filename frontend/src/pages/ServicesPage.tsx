import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { useBusiness } from '@/business/context'
import * as api from '@/api/services'
import { serviceStore } from '@/services/store'
import { ServiceForm } from '@/services/ServiceForm'
import { ServiceResults } from '@/services/ServiceResults'
import { Button } from '@/components/ui/button'
import { Input, Select } from '@/components/ui/forms'
import { Dialog } from '@/components/ui/dialog'
import { PageHeader, ErrorState } from '@/components/ui/display'

export function ServicesPage() {
  const { active } = useBusiness()
  return active ? <ServiceWorkspace key={active.id} businessId={active.id} /> : null
}
function ServiceWorkspace({ businessId }: { businessId: string }) {
  const [store] = useState(() => serviceStore(businessId, api))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [filters, setFilters] = useState<api.ServiceFilters>({ ...api.defaultServiceFilters })
  const [editing, setEditing] = useState<api.CatalogService | 'new' | null>(null)
  const [dirty, setDirty] = useState(false)
  const [confirm, setConfirm] = useState<api.CatalogService | null>(null)
  const [feedback, setFeedback] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const mounted = useRef(true)
  const busy = useRef(false)
  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; store.clear() }
  }, [store])
  useEffect(() => {
    const timeout = setTimeout(() => { void store.load(filters) }, 200)
    return () => clearTimeout(timeout)
  }, [store, filters])
  function closeEditor() {
    if (busy.current) return
    if (dirty && !window.confirm('Ungespeicherte Änderungen verwerfen?')) return
    setEditing(null); setDirty(false)
  }
  async function archive(service: api.CatalogService, archived: boolean) {
    if (busy.current) return
    busy.current = true; setPending(true); setError(''); setFeedback('')
    try {
      await store.archive(service.id, archived)
      if (mounted.current) { setConfirm(null); setFeedback(archived ? 'Leistung archiviert.' : 'Leistung wiederhergestellt.') }
    } catch { if (mounted.current) setError('Die Änderung konnte nicht bestätigt werden. Bitte lade die Liste erneut, bevor du es nochmals versuchst.') }
    finally { busy.current = false; if (mounted.current) setPending(false) }
  }
  return <div className="page-content services-page">
    <PageHeader title="Leistungen" description="Verwalte deine Dienstleistungen und Preise an einem Ort." meta={<Button id="add-service" disabled={pending} onClick={() => { setDirty(false); setEditing('new'); setFeedback(''); setError('') }}>Leistung hinzufügen</Button>} />
    <div className="customer-toolbar service-toolbar">
      <label>Leistungen suchen<Input type="search" placeholder="Name oder Beschreibung" maxLength={320} value={filters.search} disabled={pending} onChange={event => setFilters(previous => ({ ...previous, search: event.target.value }))} /></label>
      <label>Kategorie<Input type="search" placeholder="Alle Kategorien" maxLength={120} value={filters.category} disabled={pending} onChange={event => setFilters(previous => ({ ...previous, category: event.target.value }))} /></label>
      <label>Status<Select value={String(filters.active)} disabled={pending} onChange={event => setFilters(previous => ({ ...previous, active: event.target.value === 'true' }))}><option value="true">Aktive Leistungen</option><option value="false">Archivierte Leistungen</option></Select></label>
      <label>Sortierung<Select value={filters.sort} disabled={pending} onChange={event => setFilters(previous => ({ ...previous, sort: event.target.value as api.ServiceFilters['sort'] }))}><option value="NAME_ASC">Name A–Z</option><option value="NAME_DESC">Name Z–A</option><option value="PRICE_ASC">Preis aufsteigend</option><option value="PRICE_DESC">Preis absteigend</option></Select></label>
    </div>
    {feedback && <p className="auth-success" role="status">{feedback}</p>}
    {error && !confirm && <ErrorState description={error} action={<Button onClick={() => { setError(''); void store.load(filters) }}>Erneut laden</Button>} />}
    <ServiceResults state={state} filters={filters} pending={pending}
      onReload={() => void store.load(filters)}
      onReset={() => setFilters(previous => ({ ...previous, search: '', category: '' }))}
      onNew={() => { setDirty(false); setEditing('new') }}
      onEdit={row => { setDirty(false); setEditing(row); setFeedback(''); setError('') }}
      onArchive={row => { setError(''); if (row.active) setConfirm(row); else void archive(row, false) }} />
    {editing && <Dialog open className="service-dialog" returnFocus="#add-service" initialFocus={'[name="name"]'} iconClose closeDisabled={pending} title={editing === 'new' ? 'Neue Leistung' : 'Leistung bearbeiten'} description="Definiere deine Dienstleistung und ihren Preis." onClose={closeEditor}><ServiceForm onCancel={closeEditor} onDirtyChange={setDirty} service={editing === 'new' ? undefined : editing} save={async input => {
      if (busy.current) throw new Error('Busy')
      busy.current = true; setPending(true)
      try {
        await store.save(input, editing === 'new' ? undefined : editing.id)
        if (mounted.current) { setDirty(false); setEditing(null); setFeedback(editing === 'new' ? 'Leistung erstellt.' : 'Änderungen gespeichert.') }
      } finally { busy.current = false; if (mounted.current) setPending(false) }
    }} /></Dialog>}
    {confirm && <Dialog open alert returnFocus="#add-service" iconClose closeDisabled={pending} title="Leistung archivieren?" description={`${confirm.name} wird aus dem aktiven Katalog entfernt und kann jederzeit wiederhergestellt werden.`} onClose={() => { if (!pending) setConfirm(null) }} footer={<><Button variant="secondary" disabled={pending} onClick={() => setConfirm(null)}>Abbrechen</Button><Button disabled={pending} onClick={() => void archive(confirm, true)}>{pending ? 'Wird archiviert…' : 'Archivieren'}</Button></>}>{error && <p className="field-error" role="alert">{error}</p>}</Dialog>}
  </div>
}
