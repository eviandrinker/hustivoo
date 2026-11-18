import type { CatalogService, ServiceFilters } from '@/api/services'
import { formatPrice, unitLabels } from './validation'
import { ServiceActions } from './ServiceActions'
import { Button } from '@/components/ui/button'
import { DataTable } from '@/components/ui/data-table'
import { EmptyState, ErrorState, LoadingState, StatusIndicator } from '@/components/ui/display'

export function ServiceResults({ state, filters, pending, onReload, onReset, onNew, onEdit, onArchive }: {
  state: { status: 'loading' | 'ready' | 'error'; rows: CatalogService[] }
  filters: ServiceFilters
  pending: boolean
  onReload: () => void
  onReset: () => void
  onNew: () => void
  onEdit: (service: CatalogService) => void
  onArchive: (service: CatalogService) => void
}) {
  const hasFilter = Boolean(filters.search.trim() || filters.category.trim())
  return (state.status === 'loading' ? <LoadingState label="Leistungen werden geladen…" />
      : state.status === 'error' ? <ErrorState title="Leistungen konnten nicht geladen werden" description="Prüfe deine Verbindung und versuche es erneut." action={<Button onClick={() => onReload()}>Erneut laden</Button>} />
      : !state.rows.length ? <EmptyState title={hasFilter ? 'Keine passenden Leistungen' : filters.active ? 'Dein Leistungskatalog beginnt hier' : 'Keine archivierten Leistungen'} description={hasFilter ? 'Passe die Suche oder die Kategorie an.' : filters.active ? 'Lege deine erste Leistung an und halte deine Preise zentral bereit.' : 'Archivierte Leistungen kannst du hier wiederherstellen.'} action={hasFilter ? <Button variant="secondary" onClick={() => onReset()}>Filter zurücksetzen</Button> : filters.active && <Button onClick={() => onNew()}>Leistung hinzufügen</Button>} />
      : <DataTable caption="Leistungen des aktiven Unternehmens · Preise ohne Steuern" rows={state.rows} rowKey={row => row.id} columns={[
        { key: 'name', header: 'Name', render: row => <div className="service-name"><strong>{row.name}</strong>{row.description && <span>{row.description}</span>}</div> },
        { key: 'category', header: 'Kategorie', render: row => row.category ?? '—' },
        { key: 'price', header: 'Preis', render: row => <div className="service-price"><span>{formatPrice(row.unitPrice, row.currency)}</span><small>/ {unitLabels[row.unit]}</small></div> },
        { key: 'status', header: 'Status', render: row => <StatusIndicator tone={row.active ? 'positive' : 'neutral'}>{row.active ? 'Aktiv' : 'Archiviert'}</StatusIndicator> },
        { key: 'actions', header: 'Aktionen', render: row => <ServiceActions service={row} pending={pending} onEdit={onEdit} onArchive={onArchive} /> },
      ]} />)
}
