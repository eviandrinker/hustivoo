import { useCallback, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useBusiness } from '@/business/context'
import * as api from '@/api/expenses'
import { ApiError } from '@/api/client'
import { useExpenseResource } from '@/expenses/useExpenseResource'
import { validateSupplier } from '@/expenses/validation'
import { Button } from '@/components/ui/button'
import { FormField, Input, Textarea } from '@/components/ui/forms'
import { PageHeader, LoadingState, ErrorState } from '@/components/ui/display'
const blank: api.SupplierInput = { name: '', contactPerson: null, email: null, phone: null, street: null, houseNumber: null, postalCode: null, city: null, country: 'CH', vatNumber: null, iban: null, notes: null }
export function ExpenseSettingsPage() { const { active } = useBusiness(); return active ? <Workspace key={active.id} business={active.id} /> : null }
function Workspace({ business }: { business: string }) {
  const resource = useExpenseResource(useCallback(async () => ({ suppliers: [...await api.listSuppliers(business), ...await api.listSuppliers(business, '', true)], categories: await api.listCategories(business) }), [business]))
  const [form, setForm] = useState<api.SupplierInput>({ ...blank }); const [supplierId, setSupplierId] = useState<string>(); const [categoryId, setCategoryId] = useState<string>(); const [category, setCategory] = useState(''); const [search, setSearch] = useState('')
  const [error, setError] = useState(''); const [fields, setFields] = useState<Record<string, string>>({}); const [pending, setPending] = useState(false); const busy = useRef(false)
  async function run(operation: () => Promise<unknown>, saved?: () => void) {
    if (busy.current) return; busy.current = true; setPending(true); setError('')
    try { const result = await resource.store.run(async () => { await operation(); return true }); if (result) { saved?.(); await resource.store.load() } }
    catch (error) { if (error instanceof ApiError && error.status === 400) setFields(error.fields); else setError('Änderung nicht bestätigt. Ein gleicher Kategoriename kann bereits existieren. Bitte lade die Daten erneut.') }
    finally { busy.current = false; setPending(false) }
  }
  return <div className="page-content expense-page"><Link className="text-link" to="/expenses">← Ausgaben</Link><PageHeader title="Lieferanten & Kategorien" description="Stammdaten für dein Unternehmen. Historische Ausgaben behalten ihre Snapshots." />
    {error && <p role="alert" className="auth-error">{error}</p>}
    {resource.status === 'loading' && <LoadingState label="Stammdaten werden geladen…" />}
    {resource.status === 'error' && <ErrorState description="Stammdaten konnten nicht geladen werden." action={<Button onClick={() => void resource.store.load()}>Erneut laden</Button>} />}
    <div className="expense-settings-grid"><section className="surface expense-panel"><h2>Lieferanten</h2><FormField label="Lieferanten suchen"><Input type="search" value={search} onChange={e => setSearch(e.target.value)} /></FormField><ul className="receipt-list">{resource.data?.suppliers.filter(x => x.name.toLowerCase().includes(search.toLowerCase())).map(x => <li key={x.id}><span>{x.name}{x.archived ? ' · archiviert' : ''}</span><div className="page-actions"><Button variant="tertiary" disabled={pending} onClick={() => { setSupplierId(x.id); setForm(x); setFields({}) }}>Bearbeiten</Button><Button variant="tertiary" disabled={pending} onClick={() => void run(() => api.archiveSupplier(business, x.id, !x.archived))}>{x.archived ? 'Wiederherstellen' : 'Archivieren'}</Button></div></li>)}</ul>
      <h3>{supplierId ? 'Lieferant bearbeiten' : 'Neuer Lieferant'}</h3><form className="expense-editor" onSubmit={event => { event.preventDefault(); const invalid = validateSupplier(form); setFields(invalid); if (Object.keys(invalid).length) return; void run(() => api.saveSupplier(business, form, supplierId), () => { setSupplierId(undefined); setForm({ ...blank }) }) }} noValidate><fieldset disabled={pending} className="expense-grid">
        {([['name', 'Name'], ['contactPerson', 'Kontaktperson (optional)'], ['email', 'E-Mail (optional)'], ['phone', 'Telefon (optional)'], ['street', 'Strasse (optional)'], ['houseNumber', 'Hausnummer (optional)'], ['postalCode', 'Postleitzahl (optional)'], ['city', 'Ort (optional)'], ['country', 'Land'], ['vatNumber', 'UID/MWST-Nummer (optional)'], ['iban', 'IBAN (optional)']] as const).map(([key, label]) => <FormField key={key} label={label} error={fields[key]}><Input name={key} value={form[key] ?? ''} required={key === 'name'} type={key === 'email' ? 'email' : 'text'} onChange={e => setForm(previous => ({ ...previous, [key]: key === 'name' || key === 'country' ? e.target.value : e.target.value || null }))} /></FormField>)}
        <FormField label="Notizen (optional)" error={fields.notes}><Textarea value={form.notes ?? ''} onChange={e => setForm(previous => ({ ...previous, notes: e.target.value || null }))} /></FormField>
      </fieldset><div className="page-actions"><Button type="submit" disabled={pending}>Lieferant speichern</Button>{supplierId && <Button variant="secondary" onClick={() => { setSupplierId(undefined); setForm({ ...blank }) }}>Abbrechen</Button>}</div></form>
    </section><section className="surface expense-panel"><h2>Kategorien</h2><Button variant="secondary" disabled={pending} onClick={() => void run(() => api.defaultCategories(business))}>Vorgeschlagene Kategorien ergänzen</Button><ul className="receipt-list">{resource.data?.categories.map(x => <li key={x.id}><span>{x.name}{x.archived ? ' · archiviert' : ''}</span><div className="page-actions"><Button variant="tertiary" disabled={pending} onClick={() => { setCategoryId(x.id); setCategory(x.name) }}>Umbenennen</Button><Button variant="tertiary" disabled={pending} onClick={() => void run(() => api.archiveCategory(business, x.id, !x.archived))}>{x.archived ? 'Wiederherstellen' : 'Archivieren'}</Button></div></li>)}</ul>
      <form className="expense-editor" onSubmit={event => { event.preventDefault(); if (!category.trim()) return; void run(() => api.saveCategory(business, category.trim(), categoryId), () => { setCategory(''); setCategoryId(undefined) }) }}><FormField label={categoryId ? 'Neuer Kategoriename' : 'Neue Kategorie'}><Input required maxLength={120} disabled={pending} value={category} onChange={e => setCategory(e.target.value)} /></FormField><div className="page-actions"><Button type="submit" disabled={pending}>Kategorie speichern</Button>{categoryId && <Button variant="secondary" onClick={() => { setCategoryId(undefined); setCategory('') }}>Abbrechen</Button>}</div></form>
    </section></div>
  </div>
}
