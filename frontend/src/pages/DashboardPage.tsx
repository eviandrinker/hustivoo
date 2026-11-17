import { Link } from 'react-router-dom'
import { Plus, ArrowUpRight } from 'lucide-react'
import { RevenueChart } from '@/components/dashboard/RevenueChart'
import { MetricCard } from '@/components/ui/metric-card'
import { InformationPanel, PageHeader, SectionHeader, StatusIndicator } from '@/components/ui/display'
import { demoDashboard as data, formatMoney } from '@/data/dashboard'

const shortcuts = [
  { label: 'Add customer', path: '/customers' },
  { label: 'Create project', path: '/projects' },
  { label: 'Track time', path: '/time-tracking' },
  { label: 'Create invoice', path: '/invoices' },
  { label: 'Record expense', path: '/expenses' },
]

export function DashboardPage() {
  const unpaid = data.invoices.reduce((sum, invoice) => sum + invoice.amount, 0)
  const goalPercent = Math.round(data.revenue / data.goal * 100)
  return (
    <div className="page-content">
      <PageHeader title="Welcome back" description="Here’s what’s happening with your business." meta={<div className="page-actions"><Link className="button button--secondary" to="/invoices">Create invoice</Link><Link className="button button--primary" to="/projects"><Plus size={16} aria-hidden="true" />New project</Link></div>} />
      <InformationPanel className="demo-notice"><strong>Demo workspace</strong><p>Figures and activity are examples. Creation tools are coming soon; actions open module previews.</p></InformationPanel>
      <section className="business-overview" aria-label="Business overview — demo data">
        <div className="overview-label"><h2>This month at a glance</h2><span>{data.period} · CHF</span></div>
        <div className="metrics-grid">
          <MetricCard label="Revenue" value={formatMoney(data.revenue)} context="Earned this month" />
          <MetricCard label="Profit" value={formatMoney(data.revenue - data.expenses)} context="After expenses · before tax" />
          <MetricCard label="Unpaid invoices" value={formatMoney(unpaid)} context="3 outstanding · 1 overdue" />
          <MetricCard label="Hours tracked" value={`${data.hours} h`} context="Across 3 active projects" />
        </div>
      </section>
      <div className="dashboard-columns">
        <section className="surface revenue-section" aria-labelledby="revenue-title"><SectionHeader id="revenue-title" title="Revenue" description="A little progress, month by month." action={<span className="section-meta">Last 6 months</span>} /><RevenueChart /></section>
        <section className="surface attention-section" aria-labelledby="attention-title"><SectionHeader id="attention-title" title="Needs your attention" description="A few things to keep moving · demo" />
          <div className="attention-list">
            <Link to="/invoices" className="attention-item"><span className="attention-marker warning-marker" aria-hidden="true" /><div><strong>Studio North invoice is overdue</strong><p>{formatMoney(1800)} · due Nov 12</p><span className="attention-action">Review invoice <ArrowUpRight size={13} aria-hidden="true" /></span></div></Link>
            <Link to="/projects" className="attention-item"><span className="attention-marker" aria-hidden="true" /><div><strong>Website refresh is up next</strong><p>Studio North · delivery Nov 24</p><span className="attention-action">View project <ArrowUpRight size={13} aria-hidden="true" /></span></div></Link>
            <Link to="/invoices" className="attention-item"><span className="attention-marker neutral-marker" aria-hidden="true" /><div><strong>Two payments are pending</strong><p>{formatMoney(2200)} expected · Nov 25–30</p><span className="attention-action">View invoices <ArrowUpRight size={13} aria-hidden="true" /></span></div></Link>
          </div>
        </section>
      </div>
      <section className="surface projects-section" aria-labelledby="projects-title"><SectionHeader id="projects-title" title="Active projects" description="Your work in motion · demo" action={<Link className="text-link" to="/projects">View all <ArrowUpRight size={14} aria-hidden="true" /></Link>} />
        <ul className="project-list">{data.projects.map(project => <li key={project.name} className="project-item"><div className="project-info"><h3>{project.name}</h3><p>{project.client}</p></div><StatusIndicator tone="neutral">In progress</StatusIndicator><div className="project-progress"><progress className="progress" aria-label={`${project.name} demo completion`} value={project.progress} max={100} /><span>{project.progress}%</span></div><span className="project-due">Due {project.due}</span></li>)}</ul>
      </section>
      <section className="monthly-goal" aria-label="Demo monthly revenue goal"><div><h2>Monthly goal</h2><p>{formatMoney(data.revenue)} of {formatMoney(data.goal)} · demo</p></div><div className="goal-progress"><progress className="progress" aria-label="Demo monthly revenue goal" value={data.revenue} max={data.goal} /><span>{goalPercent}%</span></div><p className="goal-note">{formatMoney(data.goal - data.revenue)} to go</p></section>
      <section className="shortcuts" aria-labelledby="shortcuts-title"><div><h2 id="shortcuts-title">What’s next?</h2><p>Explore the tools taking shape.</p></div><nav aria-label="Business shortcuts">{shortcuts.map(item => <Link key={item.path} to={item.path}>{item.label}<ArrowUpRight size={13} aria-hidden="true" /></Link>)}</nav></section>
    </div>
  )
}
