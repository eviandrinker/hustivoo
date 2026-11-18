import type { MouseEvent } from 'react'
import { Download, Eye, Pencil } from 'lucide-react'
import type { QuoteFilters, QuoteSummary } from '@/api/quotes'
import { Button } from '@/components/ui/button'
import { DataTable } from '@/components/ui/data-table'
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/display'
import { QuoteStatusBadge } from './QuoteParts'
import { isExpired } from './labels'
import { formatDate, formatMoney } from './money'

/** Links keep real hrefs (new tab, copy link) while normal clicks use client-side navigation. */
function navigate(callback: () => void) {
  return (event: MouseEvent<HTMLAnchorElement>) => {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
    event.preventDefault()
    callback()
  }
}

export function QuoteResults({ state, filters, today, downloading, onReload, onReset, onNew, onOpen, onEdit, onDownload }: {
  state: { status: 'loading' | 'ready' | 'error'; rows: QuoteSummary[] }
  filters: QuoteFilters
  today: string
  downloading: string | null
  onReload: () => void
  onReset: () => void
  onNew: () => void
  onOpen: (quote: QuoteSummary) => void
  onEdit: (quote: QuoteSummary) => void
  onDownload: (quote: QuoteSummary) => void
}) {
  const filtered = Boolean(filters.search.trim() || filters.status)
  if (state.status === 'loading') return <LoadingState label="Angebote werden geladen…" />
  if (state.status === 'error') return <ErrorState title="Angebote konnten nicht geladen werden" description="Prüfe deine Verbindung und versuche es erneut." action={<Button onClick={onReload}>Erneut laden</Button>} />
  if (!state.rows.length) return <EmptyState
    title={filtered ? 'Keine passenden Angebote' : 'Noch keine Angebote'}
    description={filtered ? 'Passe die Suche oder den Statusfilter an.' : 'Erstelle dein erstes Angebot aus deinem Leistungskatalog oder mit freien Positionen.'}
    action={filtered ? <Button variant="secondary" onClick={onReset}>Filter zurücksetzen</Button> : <Button onClick={onNew}>Angebot erstellen</Button>} />
  return <DataTable caption="Angebote des aktiven Unternehmens" rows={state.rows} rowKey={row => row.id} onRowClick={onOpen} columns={[
    { key: 'number', header: 'Angebotsnummer', render: row => <a className="quote-number-link" href={`/quotes/${encodeURIComponent(row.id)}`} onClick={navigate(() => onOpen(row))}>{row.quoteNumber}</a> },
    { key: 'customer', header: 'Kunde', render: row => <span className="quote-customer">{row.customerName}</span> },
    { key: 'date', header: 'Datum', render: row => <div className="quote-dates"><time dateTime={row.issueDate}>{formatDate(row.issueDate)}</time><small>gültig bis {formatDate(row.validUntil)}</small></div> },
    { key: 'amount', header: 'Betrag', numeric: true, render: row => <span className="quote-amount">{formatMoney(row.grandTotal, row.currency)}</span> },
    { key: 'status', header: 'Status', render: row => <QuoteStatusBadge status={row.status} expired={isExpired(row.status, row.validUntil, today)} /> },
    { key: 'actions', header: 'Aktionen', render: row => <div className="quote-row-actions">
      <Button variant="tertiary" aria-label={`${row.quoteNumber} öffnen`} title="Öffnen" onClick={() => onOpen(row)}><Eye size={16} aria-hidden="true" /></Button>
      {row.status === 'DRAFT' && <Button variant="tertiary" aria-label={`${row.quoteNumber} bearbeiten`} title="Bearbeiten" onClick={() => onEdit(row)}><Pencil size={16} aria-hidden="true" /></Button>}
      <Button variant="tertiary" aria-label={`PDF von ${row.quoteNumber} herunterladen`} title="PDF herunterladen" disabled={downloading === row.id} onClick={() => onDownload(row)}><Download size={16} aria-hidden="true" /></Button>
    </div> },
  ]} />
}
