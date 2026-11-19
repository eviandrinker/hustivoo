import type { MouseEvent } from 'react'
import { Download, Eye, Pencil } from 'lucide-react'
import type { InvoiceFilters, InvoiceSummary } from '@/api/invoices'
import { Button } from '@/components/ui/button'
import { DataTable } from '@/components/ui/data-table'
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/display'
import { formatDate, formatMoney } from '@/quotes/money'
import { InvoiceStatusBadge } from './InvoiceParts'

function navigate(callback: () => void) {
  return (event: MouseEvent<HTMLAnchorElement>) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
    event.preventDefault()
    callback()
  }
}

export function InvoiceResults({ state, filters, downloading, onReload, onReset, onNew, onOpen, onEdit, onDownload }: {
  state: { status: 'loading' | 'ready' | 'error'; rows: InvoiceSummary[] }
  filters: InvoiceFilters
  downloading: string | null
  onReload: () => void
  onReset: () => void
  onNew: () => void
  onOpen: (invoice: InvoiceSummary) => void
  onEdit: (invoice: InvoiceSummary) => void
  onDownload: (invoice: InvoiceSummary) => void
}) {
  const filtered = Boolean(filters.search.trim() || filters.status)
  if (state.status === 'loading') return <LoadingState label="Rechnungen werden geladen…" />
  if (state.status === 'error') return <ErrorState title="Rechnungen konnten nicht geladen werden" description="Prüfe deine Verbindung und versuche es erneut." action={<Button onClick={onReload}>Erneut laden</Button>} />
  if (!state.rows.length) return <EmptyState
    title={filtered ? 'Keine passenden Rechnungen' : 'Noch keine Rechnungen'}
    description={filtered ? 'Passe die Suche oder den Statusfilter an.' : 'Erstelle eine Rechnung oder wandle ein angenommenes Angebot um.'}
    action={filtered ? <Button variant="secondary" onClick={onReset}>Filter zurücksetzen</Button> : <Button onClick={onNew}>Neue Rechnung</Button>} />
  return <DataTable caption="Rechnungen des aktiven Unternehmens" rows={state.rows} rowKey={row => row.id} onRowClick={onOpen} columns={[
    { key: 'number', header: 'Rechnungsnummer', render: row => <a className="quote-number-link" href={`/invoices/${encodeURIComponent(row.id)}`} onClick={navigate(() => onOpen(row))}>{row.invoiceNumber}</a> },
    { key: 'customer', header: 'Kunde', render: row => <span className="quote-customer">{row.customerName}</span> },
    { key: 'date', header: 'Datum', render: row => <time dateTime={row.issueDate}>{formatDate(row.issueDate)}</time> },
    { key: 'due', header: 'Fällig', render: row => <time className={row.overdue ? 'invoice-overdue' : undefined} dateTime={row.dueDate}>{formatDate(row.dueDate)}</time> },
    { key: 'amount', header: 'Total', numeric: true, render: row => <span className="quote-amount">{formatMoney(row.grandTotal, row.currency)}</span> },
    { key: 'open', header: 'Offen', numeric: true, render: row => row.status === 'DRAFT' || row.status === 'CANCELLED'
      ? <span className="quote-muted">—</span> : <span className="quote-amount">{formatMoney(row.openAmount, row.currency)}</span> },
    { key: 'status', header: 'Status', render: row => <InvoiceStatusBadge status={row.displayStatus} /> },
    { key: 'actions', header: 'Aktionen', render: row => <div className="quote-row-actions">
      <Button variant="tertiary" aria-label={`${row.invoiceNumber} öffnen`} title="Öffnen" onClick={() => onOpen(row)}><Eye size={16} aria-hidden="true" /></Button>
      {row.status === 'DRAFT' && <Button variant="tertiary" aria-label={`${row.invoiceNumber} bearbeiten`} title="Bearbeiten" onClick={() => onEdit(row)}><Pencil size={16} aria-hidden="true" /></Button>}
      <Button variant="tertiary" aria-label={`PDF von ${row.invoiceNumber} herunterladen`} title="PDF herunterladen" disabled={downloading === row.id} onClick={() => onDownload(row)}><Download size={16} aria-hidden="true" /></Button>
    </div> },
  ]} />
}
