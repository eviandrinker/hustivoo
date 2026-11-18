import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '@/auth/context'
import * as api from '@/api/business'
import { BusinessContext, useBusiness } from './context'
import { businessDestination, createBusinessStore } from './store'
import { Button } from '@/components/ui/button'
import { ErrorState, LoadingState } from '@/components/ui/display'
import { LogoutButton } from '@/components/LogoutButton'

export function BusinessBoundary() {
  const { user } = useAuth()
  return user ? <BusinessProvider key={user.id} /> : null
}
function BusinessProvider() {
  const [store] = useState(() => createBusinessStore(api))
  useEffect(() => {
    void store.refresh()
    return () => store.clear()
  }, [store])
  return <BusinessContext.Provider value={store}><BusinessGate /></BusinessContext.Provider>
}
function BusinessGate() {
  const business = useBusiness()
  const location = useLocation()
  const destination = businessDestination(business)
  if (destination === 'loading') return <div className="auth-gate"><LoadingState label="Dein Business wird geladen…" /></div>
  if (destination === 'error') return <div className="auth-gate"><ErrorState title="Unternehmen konnten nicht geladen werden" description="Bitte prüfe deine Verbindung und versuche es erneut." action={<Button onClick={() => void business.refresh()}>Erneut versuchen</Button>} /><LogoutButton /></div>
  if (destination === '/onboarding' && location.pathname !== '/onboarding') return <Navigate to="/onboarding" replace />
  if (destination === '/dashboard' && location.pathname === '/onboarding') return <Navigate to="/dashboard" replace />
  return <Outlet />
}
