import { Link, useLocation } from 'react-router-dom'
import { navigation } from '@/lib/navigation'
import { EmptyState, InformationPanel, PageHeader, SectionHeader } from '@/components/ui/display'

export function PlaceholderPage() {
  const { pathname } = useLocation()
  const page = navigation.find(item => item.path === pathname)
  return (
    <div className="page-content">
      <PageHeader title={page?.title ?? 'Page not found'} description={page?.description ?? 'The requested page is not available.'} meta={<span>{page ? 'In development' : 'Error 404'}</span>} />
      <InformationPanel>Preview only. Creating and editing records is not available yet.</InformationPanel>
      <section className="surface placeholder-section">
        <SectionHeader title={page ? `${page.title} workspace` : 'Return to your workspace'} />
        <EmptyState title={page ? 'A little more useful, soon.' : 'There’s nothing at this address.'} description={page ? 'This module is not available yet. The dashboard offers a preview of Hustivoo with clearly labeled demo data.' : 'Use the navigation or return to the dashboard to continue.'} action={<Link className="button button--secondary" to="/dashboard">Back to dashboard</Link>} />
      </section>
    </div>
  )
}
