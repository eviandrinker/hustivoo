import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/context'
import { authError } from '@/auth/messages'
import { Button } from './ui/button'

export function LogoutButton() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  async function logout() {
    if (pending) return
    setPending(true); setError('')
    try {
      await auth.logout()
      navigate('/login', { replace: true })
    } catch (error) { setError(authError(error, 'logout')) }
    finally { setPending(false) }
  }
  return <div className="logout-control"><Button variant="tertiary" disabled={pending} onClick={logout}>{pending ? 'Signing out…' : 'Sign out'}</Button>{error && <p role="alert" className="logout-error">{error}</p>}</div>
}
