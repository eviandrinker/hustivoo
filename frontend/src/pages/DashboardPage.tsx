import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { useBusiness } from '@/business/context'
import { financeOverview, type Finance } from '@/api/expenses'
import { useExpenseResource } from '@/expenses/useExpenseResource'
import { localToday } from '@/quotes/validation'
import { formatPrice } from '@/services/validation'
import { MetricCard } from '@/components/ui/metric-card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/forms'
import { PageHeader, LoadingState, ErrorState, EmptyState } from '@/components/ui/display'
export function DashboardPage() { const { active } = useBusiness(); return active ? <Dashboard key={active.id} business={active.id} /> : null }
const metrics = { invoiced: 'Ausgestellte Rechnungen', received: 'Zahlungseingänge', outstanding: 'Offene Forderungen', expenses: 'Erfasste Ausgaben', paidExpenses: 'Bezahlte Ausgaben', openExpenses: 'Offene Ausgaben' } as const
function Dashboard({ business }: { business: string }) {
  const [to, setTo] = useState(localToday); const [from, setFrom] = useState(() => `${localToday().slice(0, 4)}-01-01`)
  const resource = useExpenseResource(useCallback(() => financeOverview(business, from, to), [business, from, to]))
  return <div className="page-content"><PageHeader title="Deine Finanzen im Überblick" description="Rechnungen und tatsächliche Zahlungen getrennt betrachtet · CHF" meta={<div className="page-actions"><Link className="button button--secondary" to="/invoices">Rechnungen</Link><Link className="button button--primary" to="/expenses/new">Neue Ausgabe</Link></div>} />
    <div className="customer-toolbar"><label>Von<Input type="date" value={from} onChange={e => setFrom(e.target.value)} /></label><label>Bis<Input type="date" value={to} onChange={e => setTo(e.target.value)} /></label></div>
    {resource.status === 'loading' && <LoadingState label="Kennzahlen werden geladen…" />}{resource.status === 'error' && <ErrorState description="Kennzahlen nicht verfügbar. Bitte prüfe den Zeitraum (maximal fünf Jahre)." action={<Button onClick={() => void resource.store.load()}>Erneut laden</Button>} />}
    {resource.data && <FinanceOverview data={resource.data} />}
  </div>
}
export function FinanceOverview({ data }: { data: Finance }) {
  const cents = (value: string) => BigInt(value.replace('.', ''))
  const max = data.months.reduce((a, x) => [cents(x.received), cents(x.paidExpenses)].reduce((b, n) => n > b ? n : b, a), 1n)
  return <><div className="metrics-grid expense-metrics">{Object.entries(metrics).map(([key, label]) => <MetricCard key={key} label={label} value={formatPrice(data.totals[key as keyof typeof metrics], 'CHF')} context={key === 'received' || key === 'paidExpenses' ? 'Nach Zahlungsdatum · ohne Korrekturen' : 'Nach Belegdatum · aktueller Stand ohne Stornos'} />)}</div>
    <section className="surface expense-panel"><h2>Monatliche Zahlungen</h2><p className="field-hint">Zahlungseingänge und bezahlte Ausgaben · CHF</p>
      {data.months.every(x => cents(x.received) === 0n && cents(x.paidExpenses) === 0n) ? <EmptyState title="Noch keine Zahlungen im Zeitraum" description="Erfasste Zahlungseingänge und bezahlte Ausgaben erscheinen hier." /> : <div className="finance-chart">{data.months.map(month => <div key={month.month} className="finance-month"><strong>{month.month}</strong><div><span>Eingänge {formatPrice(month.received, 'CHF')}</span><div className="finance-bar income" style={{ width: `${Number(cents(month.received) * 10000n / max) / 100}%` }} /></div><div><span>Ausgaben {formatPrice(month.paidExpenses, 'CHF')}</span><div className="finance-bar expense" style={{ width: `${Number(cents(month.paidExpenses) * 10000n / max) / 100}%` }} /></div></div>)}</div>}
    </section><p className="information-panel">Nur CHF-Belege, keine Währungsumrechnung. Offene Beträge zeigen den aktuellen Stand der im Zeitraum ausgestellten Belege, keinen historischen Stichtag. Diese vereinfachte Übersicht ist weder steuerlicher Gewinn noch vollständiger Cashflow oder MWST-Abrechnung.</p></>
}
