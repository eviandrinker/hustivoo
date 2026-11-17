export function MetricCard({ label, value, context }: { label: string; value: string; context: string }) {
  return <dl className="metric-card"><dt>{label}</dt><dd className="metric-value">{value}</dd><dd className="metric-context">{context}</dd></dl>
}
