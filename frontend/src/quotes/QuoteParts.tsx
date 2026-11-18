import { useId } from 'react'
import type { ReactNode } from 'react'
import type { QuoteStatus, QuoteTaxGroup } from '@/api/quotes'
import { StatusIndicator } from '@/components/ui/display'
import { quoteStatusLabels, quoteStatusTones } from './labels'
import { formatMoney, formatRate } from './money'

export function QuoteStatusBadge({ status, expired = false }: { status: QuoteStatus; expired?: boolean }) {
  return <span className="quote-status">
    <StatusIndicator tone={quoteStatusTones[status]}>{quoteStatusLabels[status]}</StatusIndicator>
    {expired && <span className="quote-expired">Abgelaufen</span>}
  </span>
}

/** Totals panel; editor passes a preview, detail page the server's amounts. */
export function QuoteSummary({ currency, subtotal, taxTotal, grandTotal, taxGroups, note, children }: {
  currency: string; subtotal: string; taxTotal: string; grandTotal: string; taxGroups: QuoteTaxGroup[]
  note?: string; children?: ReactNode
}) {
  const id = useId()
  const taxed = taxGroups.filter(group => group.rate !== '0.00')
  return <section className="quote-summary" aria-labelledby={id}>
    <h2 id={id}>Zusammenfassung</h2>
    <dl>
      <div><dt>Zwischensumme (netto)</dt><dd>{formatMoney(subtotal, currency)}</dd></div>
      {taxed.length ? taxed.map(group => <div key={group.rate}><dt>MWST {formatRate(group.rate)} <small>auf {formatMoney(group.base, currency)}</small></dt><dd>{formatMoney(group.tax, currency)}</dd></div>)
        : <div><dt>MWST</dt><dd>{formatMoney(taxTotal, currency)}</dd></div>}
      <div className="quote-summary-total"><dt>Total</dt><dd>{formatMoney(grandTotal, currency)}</dd></div>
    </dl>
    {note && <p className="quote-summary-note">{note}</p>}
    {children && <div className="quote-summary-actions">{children}</div>}
  </section>
}
