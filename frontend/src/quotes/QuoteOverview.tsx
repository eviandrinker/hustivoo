import { Check, Download, FileText, Pencil, Send, Trash2, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Quote, QuoteAction } from '@/api/quotes'
import { Button } from '@/components/ui/button'
import { DataTable } from '@/components/ui/data-table'
import { InformationPanel } from '@/components/ui/display'
import { unitLabels } from '@/services/validation'
import { QuoteSummary } from './QuoteParts'
import { actionCopy, quoteActions } from './labels'
import { formatDate, formatMoney, formatQuantity, formatRate } from './money'

const actionIcons: Record<QuoteAction, typeof Send> = { send: Send, accept: Check, reject: X }

/** Status-dependent actions: only drafts can be edited/deleted; issued quotes are read-only. */
export function QuoteActions({ quote, pending, downloading, onEdit, onDelete, onAction, onDownload, onInvoice }: {
  quote: Quote; pending: boolean; downloading: boolean
  onEdit: () => void; onDelete: () => void; onAction: (action: QuoteAction) => void; onDownload: () => void
  /** Accepted quotes become invoices; an existing invoice is linked instead. */
  onInvoice?: () => void
}) {
  return <div className="page-actions quote-actions">
    {quote.status === 'DRAFT' && <Button variant="secondary" disabled={pending} onClick={onEdit}><Pencil size={15} aria-hidden="true" />Bearbeiten</Button>}
    <Button variant="secondary" disabled={pending || downloading} onClick={onDownload}><Download size={15} aria-hidden="true" />{downloading ? 'PDF wird erstellt…' : 'PDF'}</Button>
    {quoteActions[quote.status].map(action => {
      const Icon = actionIcons[action]
      return <Button key={action} variant={action === 'reject' ? 'secondary' : 'primary'} disabled={pending} onClick={() => onAction(action)}><Icon size={15} aria-hidden="true" />{actionCopy[action].label}</Button>
    })}
    {quote.status === 'ACCEPTED' && (quote.invoice
      ? <Link className="button button--primary" to={`/invoices/${encodeURIComponent(quote.invoice.id)}`}><FileText size={15} aria-hidden="true" />Rechnung {quote.invoice.invoiceNumber}</Link>
      : onInvoice && <Button disabled={pending} onClick={onInvoice}><FileText size={15} aria-hidden="true" />{pending ? 'Rechnung wird erstellt…' : 'Rechnung erstellen'}</Button>)}
    {quote.status === 'DRAFT' && <Button variant="tertiary" className="quote-delete" disabled={pending} onClick={onDelete}><Trash2 size={15} aria-hidden="true" />Löschen</Button>}
  </div>
}

function time(value: string) {
  return new Intl.DateTimeFormat('de-CH', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

export function QuoteOverview({ quote, expired }: { quote: Quote; expired: boolean }) {
  const customer = quote.customer
  const address = [customer.companyName !== customer.name ? customer.companyName : null, customer.contactName !== customer.name ? customer.contactName : null,
    customer.street, [customer.postalCode, customer.city].filter(Boolean).join(' '), customer.country].filter(Boolean)
  return <div className="quote-detail-layout">
    <div className="quote-editor-main">
      {quote.status !== 'DRAFT' && <InformationPanel className="quote-readonly">
        {quote.status === 'SENT' ? 'Gesendete Angebote sind schreibgeschützt. Markiere das Angebot als angenommen oder abgelehnt, sobald der Kunde entschieden hat.'
          : 'Dieses Angebot ist abgeschlossen und bleibt unverändert erhalten.'}
        {expired && <strong> Die Gültigkeit ist abgelaufen.</strong>}
      </InformationPanel>}
      <section className="quote-card quote-parties" aria-label="Kunde und Konditionen">
        <div>
          <h2>Kunde</h2>
          <p className="quote-party-name">{customer.name}</p>
          {address.map(line => <p key={line}>{line}</p>)}
          {customer.email && <p className="quote-muted">{customer.email}</p>}
        </div>
        <dl className="quote-meta">
          <div><dt>Kundennummer</dt><dd>{customer.customerNumber}</dd></div>
          <div><dt>Datum</dt><dd>{formatDate(quote.issueDate)}</dd></div>
          <div><dt>Gültig bis</dt><dd>{formatDate(quote.validUntil)}</dd></div>
          <div><dt>Währung</dt><dd>{quote.currency}</dd></div>
          {quote.sentAt && <div><dt>Gesendet</dt><dd>{time(quote.sentAt)}</dd></div>}
          {quote.decidedAt && <div><dt>Entschieden</dt><dd>{time(quote.decidedAt)}</dd></div>}
        </dl>
      </section>
      <section className="quote-card" aria-label="Positionen">
        {quote.introductionText && <p className="quote-text">{quote.introductionText}</p>}
        {quote.items.length ? <DataTable caption={`Positionen von ${quote.quoteNumber}`} rows={quote.items} rowKey={item => item.id} columns={[
          { key: 'position', header: 'Pos.', render: item => item.position },
          { key: 'description', header: 'Beschreibung', render: item => { const [title, ...rest] = item.description.split('\n'); return <div className="quote-line-description"><strong>{title}</strong>{rest.length > 0 && <span>{rest.join('\n')}</span>}</div> } },
          { key: 'quantity', header: 'Menge', numeric: true, render: item => `${formatQuantity(item.quantity)} ${unitLabels[item.unit]}` },
          { key: 'price', header: 'Einzelpreis', numeric: true, render: item => formatMoney(item.unitPrice, quote.currency) },
          { key: 'tax', header: 'MWST', numeric: true, render: item => formatRate(item.taxRate) },
          { key: 'total', header: 'Betrag', numeric: true, render: item => formatMoney(item.lineSubtotal, quote.currency) },
        ]} /> : <p className="quote-empty-items">Dieses Angebot enthält noch keine Positionen.</p>}
        {quote.closingText && <p className="quote-text">{quote.closingText}</p>}
      </section>
      {quote.notes && <InformationPanel className="quote-notes"><strong>Interne Notiz</strong><p>{quote.notes}</p></InformationPanel>}
    </div>
    <aside className="quote-editor-aside">
      <QuoteSummary currency={quote.currency} subtotal={quote.subtotal} taxTotal={quote.taxTotal} grandTotal={quote.grandTotal} taxGroups={quote.taxGroups} />
    </aside>
  </div>
}
