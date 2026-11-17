import { demoDashboard, formatMoney } from '@/data/dashboard'

export function RevenueChart() {
  return (
    <figure className="revenue-chart" aria-label="Demo revenue, June to November 2026, in Swiss francs">
      <div className="chart-layout">
        <div className="chart-axis" aria-hidden="true"><span>8k</span><span>4k</span><span>0</span></div>
        <div className="chart-bars">
          {demoDashboard.revenueHistory.map(({ month, value }) => (
            <div className="chart-column" key={month}>
              <div className="chart-bar" style={{ height: `${value / 8000 * 100}%` }}>
                <span className="chart-value">{new Intl.NumberFormat('en-CH').format(value)}</span>
              </div>
              <span className="chart-month">{month}</span>
            </div>
          ))}
        </div>
      </div>
      <figcaption>Monthly revenue · CHF · sample figures<span className="sr-only">. {demoDashboard.revenueHistory.map(item => `${item.month}: ${formatMoney(item.value)}`).join('; ')}</span></figcaption>
    </figure>
  )
}
