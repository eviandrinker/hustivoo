import { useEffect } from 'react'
import { Link, Outlet, useLocation } from 'react-router-dom'
import { navigation } from '@/lib/navigation'
import { useBackendHealth } from '@/hooks/useBackendHealth'
import { StatusIndicator } from '@/components/ui/display'
import { LogoutButton } from '@/components/LogoutButton'
import { Sidebar } from '@/components/Sidebar'

export function AppLayout() {
  const { pathname } = useLocation()
  const title = pathname.startsWith('/customers/') ? 'Kundendetails' : navigation.find(item => item.path === pathname)?.title ?? 'Page not found'
  const status = useBackendHealth()
  useEffect(() => {
    document.title = `${title} · Hustivoo`
    document.getElementById('main-content')?.focus()
  }, [title])
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">Skip to content</a>
      <Sidebar />
      <div className="workspace">
        <header className="topbar"><span className="topbar-title">Workspace <span aria-hidden="true">/</span> <strong>{title}</strong></span><div className="topbar-right"><Link className="text-link" to="/time-tracking">Track time</Link><LogoutButton /><span className="avatar" role="img" aria-label="User avatar placeholder">HU</span></div></header>
        <main id="main-content" tabIndex={-1}><Outlet /></main>
        <footer className="workspace-footer"><span>Hustivoo · Margraf Digital</span><span role="status"><StatusIndicator tone={status === 'Connected' ? 'positive' : status === 'Unavailable' ? 'negative' : 'neutral'}>Backend · {status}</StatusIndicator></span></footer>
      </div>
    </div>
  )
}
