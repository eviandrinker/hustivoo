import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './context'
import { Button } from '@/components/ui/button'
import { ErrorState, LoadingState } from '@/components/ui/display'

export function AuthGate({ publicOnly = false }: { publicOnly?: boolean }) {
  const auth = useAuth()
  const location = useLocation()
  if (auth.status === 'loading') return <div className="auth-gate"><LoadingState label="Opening your workspace…" /></div>
  if (auth.status === 'error') return <div className="auth-gate"><ErrorState title="We couldn’t connect" description="Your session couldn’t be checked. Please try again." action={<Button onClick={auth.retry}>Try again</Button>} /></div>
  if (publicOnly) return auth.user ? <Navigate to="/dashboard" replace /> : <Outlet />
  return auth.user ? <Outlet /> : <Navigate to="/login" replace state={{ from: location.pathname + location.search + location.hash }} />
}
