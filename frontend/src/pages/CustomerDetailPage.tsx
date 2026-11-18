import { useEffect, useRef, useState, useSyncExternalStore } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useBusiness } from '@/business/context'
import * as api from '@/api/customers'
import { customerDetailStore } from '@/customers/detail-store'
import { CustomerForm } from '@/customers/CustomerForm'
import { ContactForm } from '@/customers/ContactForm'
import { CustomerStatusBadge } from '@/customers/CustomerStatusBadge'
import { customerStatusLabels, activityLabels } from '@/customers/labels'
import { validateNote } from '@/customers/validation'
import { Button } from '@/components/ui/button'
import { Select, FormField, Textarea } from '@/components/ui/forms'
import { Dialog } from '@/components/ui/dialog'
import { PageHeader, SectionHeader, EmptyState, ErrorState, LoadingState } from '@/components/ui/display'

const sections = ['Übersicht', 'Kontaktpersonen', 'Notizen', 'Aktivitäten'] as const
function time(value: string) { return new Intl.DateTimeFormat('de-CH', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) }
export function CustomerDetailPage() {
  const { active } = useBusiness()
  const { customerId } = useParams()
  return active && customerId ? <DetailWorkspace key={`${active.id}:${customerId}`} businessId={active.id} customerId={customerId} /> : null
}
function DetailWorkspace({ businessId, customerId }: { businessId: string; customerId: string }) {
  const [store] = useState(() => customerDetailStore(businessId, customerId, api))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [section, setSection] = useState(0)
  const [editing, setEditing] = useState(false)
  const [contact, setContact] = useState<api.Contact | 'new' | null>(null)
  const [confirmation, setConfirmation] = useState<{ kind: 'archive' } | { kind: 'contact'; contact: api.Contact } | null>(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [note, setNote] = useState('')
  const [noteError, setNoteError] = useState<string>()
  const mounted = useRef(true)
  const busy = useRef(false)
  const tabs = useRef<HTMLDivElement>(null)
  useEffect(() => { mounted.current = true; void store.load(); return () => { mounted.current = false; store.clear() } }, [store])
  async function mutate(operation: () => Promise<unknown>, message: string) {
    if (busy.current) throw new Error('Busy')
    busy.current = true; setPending(true); setError(''); setSuccess('')
    try {
      const applied = await store.mutate(operation)
      if (mounted.current && applied) { setSuccess(message); setEditing(false); setContact(null); setConfirmation(null) }
    } finally { busy.current = false; if (mounted.current) setPending(false) }
  }
  async function act(operation: () => Promise<unknown>, message: string) {
    try { await mutate(operation, message) }
    catch { if (mounted.current) setError('Die Änderung konnte nicht bestätigt werden. Bitte lade die Daten erneut, bevor du es wieder versuchst.') }
  }
  const data = state.data
  const customer = data?.customer
  return <div className="page-content customer-detail">
    <Link className="text-link" to="/customers">← Alle Kunden</Link>
    {state.status === 'loading' && <LoadingState label="Kundendetails werden geladen…" />}
    {state.status === 'error' && <ErrorState title="Kunde nicht verfügbar" description="Der Kunde ist nicht mehr verfügbar oder die Verbindung wurde unterbrochen." action={<Button onClick={() => void store.load()}>Erneut laden</Button>} />}
    {success && <p className="auth-success" role="status">{success}</p>}
    {customer && data && <>
      <PageHeader title={customer.displayName} description={`${customer.customerNumber} · ${customer.type === 'COMPANY' ? 'Unternehmen' : 'Privatperson'}`} meta={<div className="page-actions"><Button variant="secondary" disabled={pending} onClick={() => setEditing(true)}>Bearbeiten</Button><Button variant="tertiary" disabled={pending} onClick={() => customer.archived ? void act(() => api.archiveCustomer(businessId, customerId, false), 'Kunde wiederhergestellt.') : setConfirmation({ kind: 'archive' })}>{customer.archived ? 'Wiederherstellen' : 'Archivieren'}</Button></div>} />
      <div className="crm-summary"><CustomerStatusBadge status={customer.status} />{customer.archived && <span className="crm-archive-label">Archiviert</span>}<label className="crm-status-select">Status ändern<Select aria-label="Kundenstatus ändern" value={customer.status} disabled={pending} onChange={event => void act(() => api.updateCustomerStatus(businessId, customerId, event.target.value as api.CustomerStatus), 'Kundenstatus geändert.')}>{Object.entries(customerStatusLabels).map(([value,label]) => <option key={value} value={value}>{label}</option>)}</Select></label></div>
      {error && !confirmation && <ErrorState description={error} action={<Button variant="secondary" onClick={() => { setError(''); void store.load() }}>Erneut laden</Button>} />}
      <div ref={tabs} className="crm-tabs" role="tablist" aria-label="Kundenbereiche" onKeyDown={event => {
        let next: number
        if (event.key === 'ArrowRight') next = (section + 1) % sections.length
        else if (event.key === 'ArrowLeft') next = (section + sections.length - 1) % sections.length
        else if (event.key === 'Home') next = 0
        else if (event.key === 'End') next = sections.length - 1
        else return
        event.preventDefault(); setSection(next); tabs.current?.querySelectorAll<HTMLButtonElement>('button')[next]?.focus()
      }}>{sections.map((label,index) => <button key={label} role="tab" id={`crm-tab-${index}`} aria-selected={section === index} aria-controls="crm-panel" tabIndex={section === index ? 0 : -1} onClick={() => setSection(index)}>{label}{index === 1 ? ` (${data.contacts.length})` : index === 2 ? ` (${data.notes.length})` : ''}</button>)}</div>
      <section id="crm-panel" role="tabpanel" aria-labelledby={`crm-tab-${section}`} tabIndex={0} className="crm-panel">
        {section === 0 && <><SectionHeader title="Kundenübersicht" /><dl className="crm-overview">{[
          ['Kundennummer', customer.customerNumber], ['Firma', customer.companyName], ['Kontakt', [customer.firstName, customer.lastName].filter(Boolean).join(' ')], ['E-Mail', customer.email], ['Telefon', customer.phone], ['Adresse', [customer.street, [customer.postalCode, customer.city].filter(Boolean).join(' '), customer.country].filter(Boolean).join('\n')], ['Erstellt', time(customer.createdAt)], ['Aktualisiert', time(customer.updatedAt)],
        ].map(([label,value]) => <div key={label}><dt>{label}</dt><dd>{value || '—'}</dd></div>)}</dl>{customer.notes && <aside className="information-panel"><strong>Stammnotiz</strong><p className="crm-note-content">{customer.notes}</p></aside>}</>}
        {section === 1 && <><SectionHeader title="Kontaktpersonen" description={customer.type === 'PRIVATE' ? 'Mehrere Kontaktpersonen sind für Firmenkunden verfügbar.' : 'Die richtigen Menschen für jedes Anliegen.'} action={customer.type === 'COMPANY' && <Button disabled={pending} onClick={() => setContact('new')}>Kontakt hinzufügen</Button>} />{data.contacts.length ? <ul className="crm-contact-list">{data.contacts.map(item => <li key={item.id}><div><h3>{item.firstName} {item.lastName} {item.primaryContact && <span className="crm-status crm-status--active">Hauptkontakt</span>}</h3><p>{item.jobTitle || 'Keine Funktion hinterlegt'}</p><p>{[item.email, item.phone].filter(Boolean).join(' · ') || 'Keine Kontaktdaten hinterlegt'}</p></div><div className="customer-actions"><Button variant="tertiary" disabled={pending} aria-label={`${item.firstName} ${item.lastName} bearbeiten`} onClick={() => setContact(item)}>Bearbeiten</Button><Button variant="tertiary" disabled={pending} aria-label={`${item.firstName} ${item.lastName} entfernen`} onClick={() => setConfirmation({ kind: 'contact', contact: item })}>Entfernen</Button></div></li>)}</ul> : <EmptyState title="Noch keine Kontaktpersonen" description={customer.type === 'COMPANY' ? 'Ergänze deine Ansprechpartner und markiere bei Bedarf einen Hauptkontakt.' : 'Die Kontaktdaten dieser Privatperson findest du in der Übersicht.'} />}</>}
        {section === 2 && <><SectionHeader title="Interne Notizen" description="Nur für Mitglieder deines Unternehmens sichtbar." /><form className="crm-note-form" onSubmit={event => {
          event.preventDefault(); const invalid = validateNote(note); setNoteError(invalid)
          if (invalid) event.currentTarget.querySelector('textarea')?.focus()
          if (!invalid) void act(() => api.addNote(businessId, customerId, note.trim()).then(result => { if (mounted.current) setNote(''); return result }), 'Notiz hinzugefügt.')
        }}><FormField label="Neue Notiz" error={noteError}><Textarea name="content" required aria-label="Neue interne Notiz" value={note} disabled={pending} onChange={event => { setNote(event.target.value); setNoteError(undefined) }} placeholder="Was solltest du für das nächste Gespräch wissen?" /></FormField><Button type="submit" disabled={pending}>{pending ? 'Wird gespeichert…' : 'Notiz hinzufügen'}</Button></form>{data.notes.length ? <ol className="crm-history">{data.notes.map(item => <li key={item.id}><p className="crm-note-content">{item.content}</p><p className="crm-meta">{item.authorName} · <time dateTime={item.createdAt}>{time(item.createdAt)}</time></p></li>)}</ol> : <EmptyState title="Noch keine Notizen" description="Halte Absprachen und wichtige Informationen hier fest." />}</>}
        {section === 3 && <><SectionHeader title="Aktivitäten" description="Automatisch erfasste Änderungen · neueste zuerst" />{!data.activities.length && <EmptyState title="Noch keine Aktivitäten" description="Neue Änderungen erscheinen hier automatisch." />}<ol className="crm-history">{data.activities.map(item => <li key={item.id}><strong>{activityLabels[item.event]}</strong><p className="crm-meta">{item.authorName} · <time dateTime={item.createdAt}>{time(item.createdAt)}</time></p></li>)}</ol></>}
      </section>
      {editing && <Dialog open title="Kundendaten bearbeiten" onClose={() => { if (!pending) setEditing(false) }}><CustomerForm customer={customer} save={input => mutate(() => api.saveCustomer(businessId, input, customerId), 'Kundendaten gespeichert.')} /></Dialog>}
      {contact && <Dialog open title={contact === 'new' ? 'Kontaktperson hinzufügen' : 'Kontaktperson bearbeiten'} onClose={() => { if (!pending) setContact(null) }}><ContactForm contact={contact === 'new' ? undefined : contact} save={input => mutate(() => api.saveContact(businessId, customerId, input, contact === 'new' ? undefined : contact.id), 'Kontaktperson gespeichert.')} /></Dialog>}
      {confirmation && <Dialog open alert title={confirmation.kind === 'archive' ? 'Kunde archivieren?' : 'Kontaktperson entfernen?'} description={confirmation.kind === 'archive' ? 'Der Kunde bleibt erhalten und kann jederzeit wiederhergestellt werden.' : `${confirmation.contact.firstName} ${confirmation.contact.lastName} wird dauerhaft entfernt.`} onClose={() => { if (!pending) setConfirmation(null) }} footer={<><Button variant="secondary" disabled={pending} onClick={() => setConfirmation(null)}>Abbrechen</Button><Button variant={confirmation.kind === 'contact' ? 'destructive' : 'primary'} disabled={pending} onClick={() => void act(() => confirmation.kind === 'archive' ? api.archiveCustomer(businessId, customerId, true) : api.removeContact(businessId, customerId, confirmation.contact.id), confirmation.kind === 'archive' ? 'Kunde archiviert.' : 'Kontaktperson entfernt.')}>{pending ? 'Wird ausgeführt…' : 'Bestätigen'}</Button></>}>{error && <p role="alert" className="field-error">{error}</p>}</Dialog>}
    </>}
  </div>
}
