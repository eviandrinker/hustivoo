import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { customerStatusLabels } from '@/customers/labels'
import { CustomerStatusBadge } from '@/customers/CustomerStatusBadge'
import { useBusiness } from '@/business/context'
import * as api from '@/api/customers'
import { customerStore } from '@/customers/store'
import { CustomerForm } from '@/customers/CustomerForm'
import { Button } from '@/components/ui/button'
import { Checkbox, Input, Select } from '@/components/ui/forms'
import { Dialog } from '@/components/ui/dialog'
import { DataTable } from '@/components/ui/data-table'
import { PageHeader, EmptyState, ErrorState, LoadingState, StatusIndicator } from '@/components/ui/display'

export function CustomersPage() {
  const { active } = useBusiness()
  return active ? <CustomerWorkspace key={active.id} businessId={active.id} /> : null
}
function CustomerWorkspace({ businessId }: { businessId: string }) {
  const [store] = useState(() => customerStore(businessId, api))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const navigate = useNavigate()
  const [filters, setFilters] = useState<api.CustomerFilters>({ sort: 'NAME_ASC' })
  const [search, setSearch] = useState('')
  const [archived, setArchived] = useState(false)
  const [editing, setEditing] = useState<api.Customer | 'new' | null>(null)
  const [confirm, setConfirm] = useState<api.Customer | null>(null)
  const [feedback, setFeedback] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const [saving, setSaving] = useState(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; store.clear() } }, [store])
  useEffect(() => {
    const timeout = setTimeout(() => { void store.load(search, archived, filters) }, 200)
    return () => clearTimeout(timeout)
  }, [store, search, archived, filters])
  async function changeArchive(customer: api.Customer, value: boolean) {
    if (pending) return
    setPending(true); setError('')
    try {
      await store.archive(customer.id, value)
      if (mounted.current) { setConfirm(null); setFeedback(value ? 'Kunde archiviert.' : 'Kunde wiederhergestellt.') }
    } catch { if (mounted.current) setError('Die Änderung konnte nicht bestätigt werden. Bitte lade die Kundenliste erneut.') }
    finally { if (mounted.current) setPending(false) }
  }
  return <div className="page-content">
    <PageHeader title="Kunden" description="Verwalte deine Kunden und Kontakte an einem Ort." meta={<Button disabled={saving} onClick={() => { setEditing('new'); setFeedback('') }}>Kunde hinzufügen</Button>} />
    <div className="customer-toolbar"><label> Kunden suchen<Input type="search" placeholder="Nummer, Name, Firma oder E-Mail" value={search} maxLength={320} onChange={event => setSearch(event.target.value)} /></label><label>Status<Select value={filters.status ?? ''} onChange={event => setFilters(previous => ({ ...previous, status: event.target.value as api.CustomerStatus | '' }))}><option value="">Alle Status</option>{Object.entries(customerStatusLabels).map(([value,label]) => <option key={value} value={value}>{label}</option>)}</Select></label>
    <label>Kundentyp<Select value={filters.type ?? ''} onChange={event => setFilters(previous => ({ ...previous, type: event.target.value as api.CustomerInput['type'] | '' }))}><option value="">Alle Typen</option><option value="PRIVATE">Privatperson</option><option value="COMPANY">Unternehmen</option></Select></label>
    <label>Sortierung<Select value={filters.sort} onChange={event => setFilters(previous => ({ ...previous, sort: event.target.value as api.CustomerFilters['sort'] }))}><option value="NAME_ASC">Name A–Z</option><option value="NAME_DESC">Name Z–A</option><option value="CREATED_DESC">Neueste zuerst</option><option value="CREATED_ASC">Älteste zuerst</option></Select></label>
    <label className="archive-toggle"><Checkbox checked={archived} onChange={event => setArchived(event.target.checked)} />Archivierte anzeigen</label></div>
    {feedback && <p className="auth-success" role="status">{feedback}</p>}
    {error && !confirm && <ErrorState description={error} action={<Button variant="secondary" onClick={() => { setError(''); void store.load() }}>Erneut laden</Button>} />}
    {state.status === 'loading' ? <LoadingState label="Kunden werden geladen…" /> : state.status === 'error' ? <ErrorState title="Kunden konnten nicht geladen werden" description="Prüfe deine Verbindung und versuche es erneut." action={<Button onClick={() => void store.load()}>Erneut laden</Button>} /> : !state.rows.length ? <EmptyState title={search || filters.status || filters.type ? 'Keine Suchergebnisse' : 'Noch keine Kunden'} description={search || filters.status || filters.type ? 'Versuche einen anderen Namen oder eine andere E-Mail-Adresse.' : 'Lege deinen ersten Kunden an, um Kontakte zentral zu verwalten.'} action={!search && !filters.status && !filters.type && <Button onClick={() => setEditing('new')}>Kunde hinzufügen</Button>} /> : <DataTable caption="Kunden des aktiven Unternehmens" rows={state.rows} onRowClick={row => navigate(`/customers/${row.id}`)} rowKey={row => row.id} columns={[
      { key: 'number', header: 'Nummer', render: row => row.customerNumber },
      { key: 'name', header: 'Kundenname', render: row => <Link className="customer-name-link" to={`/customers/${row.id}`}>{row.displayName}</Link> },
      { key: 'crmStatus', header: 'Kundenstatus', render: row => <CustomerStatusBadge status={row.status} /> },
      { key: 'type', header: 'Typ', render: row => row.type === 'PRIVATE' ? 'Privatperson' : 'Unternehmen' },
      { key: 'email', header: 'E-Mail', render: row => row.email ?? '—' },
      { key: 'phone', header: 'Telefon', render: row => row.phone ?? '—' },
      { key: 'city', header: 'Ort', render: row => row.city ?? '—' },
      { key: 'status', header: 'Archiv', render: row => <StatusIndicator tone={row.archived ? 'neutral' : 'positive'}>{row.archived ? 'Archiviert' : 'Aktiv'}</StatusIndicator> },
      { key: 'actions', header: 'Aktionen', render: row => <div className="customer-actions"><Button variant="tertiary" disabled={saving} onClick={() => { setEditing(row); setFeedback('') }} aria-label={`${row.displayName} bearbeiten`}>Bearbeiten</Button><Button variant="tertiary" disabled={pending} aria-label={`${row.displayName} ${row.archived ? 'wiederherstellen' : 'archivieren'}`} onClick={() => { setError(''); if (row.archived) void changeArchive(row, false); else setConfirm(row) }}>{row.archived ? 'Wiederherstellen' : 'Archivieren'}</Button></div> },
    ]} />}
    {editing && <Dialog open onClose={() => { if (!saving) setEditing(null) }} title={editing === 'new' ? 'Kunde hinzufügen' : 'Kunde bearbeiten'}><CustomerForm customer={editing === 'new' ? undefined : editing} save={async input => {
      setSaving(true)
      try {
        await store.save(input, editing === 'new' ? undefined : editing.id)
        if (mounted.current) { setEditing(null); setFeedback(editing === 'new' ? 'Kunde erstellt.' : 'Änderungen gespeichert.') }
      } finally { if (mounted.current) setSaving(false) }
    }} /></Dialog>}
    {confirm && <Dialog open alert onClose={() => { if (!pending) setConfirm(null) }} title="Kunde archivieren?" description={`${confirm.displayName} wird aus der aktiven Liste entfernt. Du kannst den Kunden jederzeit wiederherstellen.`} footer={<><Button variant="secondary" disabled={pending} onClick={() => setConfirm(null)}>Abbrechen</Button><Button disabled={pending} onClick={() => void changeArchive(confirm, true)}>{pending ? 'Wird archiviert…' : 'Archivieren'}</Button></>}>{error && <p role="alert" className="field-error">{error}</p>}</Dialog>}
  </div>
}
