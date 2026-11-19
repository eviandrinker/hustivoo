import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { useBusiness } from '@/business/context'
import * as api from '@/api/expenses'
import { useExpenseResource } from '@/expenses/useExpenseResource'
import { statusLabels } from '@/expenses/validation'
import { formatPrice } from '@/services/validation'
import { Button } from '@/components/ui/button'
import { Input, Select } from '@/components/ui/forms'
import { DataTable } from '@/components/ui/data-table'
import { PageHeader, ErrorState, LoadingState, EmptyState, StatusIndicator } from '@/components/ui/display'
export function ExpensesPage() { const { active } = useBusiness(); return active ? <Workspace key={active.id} business={active.id} /> : null }
function Workspace({ business }: { business: string }) {
  const [filters, setFilters] = useState({ ...api.defaultFilters })
  const refs = useExpenseResource(useCallback(async () => ({ suppliers: [...await api.listSuppliers(business), ...await api.listSuppliers(business, '', true)], categories: await api.listCategories(business) }), [business]))
  const list = useExpenseResource(useCallback(() => api.listExpenses(business, filters), [business, filters]))
  function filter(key: keyof api.ExpenseFilters, value: string) { setFilters(previous => ({ ...previous, [key]: value })) }
  return <div className="page-content expense-page">
    <PageHeader title="Ausgaben" description="Belege, Lieferanten und Zahlungen übersichtlich an einem Ort." meta={<div className="page-actions"><Link className="button button--secondary" to="/expenses/settings">Lieferanten & Kategorien</Link><Link className="button button--primary" to="/expenses/new">Neue Ausgabe</Link></div>} />
    <div className="customer-toolbar expense-filters">
      <label>Suche<Input type="search" maxLength={320} value={filters.search} placeholder="Lieferant, Beschreibung, Belegnummer" onChange={e => filter('search', e.target.value)} /></label>
      <label>Von<Input type="date" value={filters.from} onChange={e => filter('from', e.target.value)} /></label><label>Bis<Input type="date" value={filters.to} onChange={e => filter('to', e.target.value)} /></label>
      <label>Lieferant<Select value={filters.supplierId} onChange={e => filter('supplierId', e.target.value)}><option value="">Alle Lieferanten</option>{refs.data?.suppliers.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}</Select></label>
      <label>Kategorie<Select value={filters.categoryId} onChange={e => filter('categoryId', e.target.value)}><option value="">Alle Kategorien</option>{refs.data?.categories.map(x => <option key={x.id} value={x.id}>{x.name}</option>)}</Select></label>
      <label>Status<Select value={filters.status} onChange={e => filter('status', e.target.value)}><option value="">Alle Status</option>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select></label>
    </div>
    {refs.status === 'error' && <ErrorState description="Filterdaten konnten nicht geladen werden." action={<Button onClick={() => void refs.store.load()}>Filter erneut laden</Button>} />}
    {list.status === 'loading' ? <LoadingState label="Ausgaben werden geladen…" /> : list.status === 'error' ? <ErrorState description="Ausgaben konnten nicht geladen werden. Prüfe den Zeitraum und deine Verbindung." action={<Button onClick={() => void list.store.load()}>Erneut laden</Button>} /> : list.data && <>
      <div className="expense-totals"><div><span>Gesamt · gefiltert, ohne Stornos</span><strong>{Object.entries(list.data.totals).map(([c, n]) => formatPrice(n, c)).join(' · ') || 'Keine Beträge'}</strong></div><div><span>Aktueller Monat · gefiltert</span><strong>{Object.entries(list.data.monthTotals).map(([c, n]) => formatPrice(n, c)).join(' · ') || 'Keine Beträge'}</strong></div></div>
      {!list.data.items.length ? <EmptyState title="Keine Ausgaben gefunden" description="Erfasse eine Ausgabe oder passe deine Filter an." action={<Link className="button button--primary" to="/expenses/new">Neue Ausgabe</Link>} /> : <DataTable caption="Ausgaben des aktiven Unternehmens" rows={list.data.items} rowKey={x => x.id} columns={[
        { key: 'date', header: 'Datum', render: x => x.expenseDate }, { key: 'supplier', header: 'Lieferant', render: x => x.supplier.name },
        { key: 'description', header: 'Beschreibung', render: x => <Link className="text-link" to={`/expenses/${x.id}`}>{x.description}</Link> },
        { key: 'category', header: 'Kategorie', render: x => x.categoryName }, { key: 'amount', header: 'Brutto', render: x => formatPrice(x.grossAmount, x.currency) },
        { key: 'status', header: 'Status', render: x => <StatusIndicator tone={x.displayStatus === 'PAID' ? 'positive' : x.displayStatus === 'OVERDUE' ? 'negative' : 'neutral'}>{statusLabels[x.displayStatus]}</StatusIndicator> },
        { key: 'receipts', header: 'Belege', render: x => x.receiptCount ?? 0 },
      ]} />}
    </>}
  </div>
}
