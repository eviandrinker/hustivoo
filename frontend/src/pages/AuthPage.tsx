import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/context'
import { authError } from '@/auth/messages'
import { Button } from '@/components/ui/button'
import { FormField, Input } from '@/components/ui/forms'
import { navigation } from '@/lib/navigation'

export function AuthPage({ register = false }: { register?: boolean }) {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [pending, setPending] = useState(false)
  const inFlight = useRef(false)
  const errorRef = useRef<HTMLDivElement>(null)
  const state = location.state as { from?: unknown; registered?: boolean } | null
  const destination = typeof state?.from === 'string' ? state.from : ''
  const from = (navigation.some(item => item.path === destination.split(/[?#]/)[0]) || /^\/customers\/[\da-f-]{36}$/.test(destination.split(/[?#]/)[0])) ? destination : '/dashboard'

  useEffect(() => { document.title = `${register ? 'Create account' : 'Sign in'} · Hustivoo` }, [register])
  useEffect(() => { if (error) errorRef.current?.focus() }, [error])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (inFlight.current) return
    setError('')
    const errors: Record<string, string> = {}
    const normalized = email.trim().toLowerCase()
    if (!normalized || normalized.length > 320 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalized)) errors.email = 'Enter a valid email address.'
    if (!password.trim()) errors.password = 'Enter your password.'
    if (register) {
      if (password.length < 12) errors.password = 'Use at least 12 characters.'
      if (password.length > 72 || new TextEncoder().encode(password).length > 72) errors.password = 'Use at most 72 characters and 72 UTF-8 bytes. Some characters use more than one byte.'
      if (confirmation !== password) errors.confirmation = 'Your passwords don’t match.'
    }
    setFieldErrors(errors)
    if (Object.keys(errors).length) {
      setError('Please check the highlighted fields.')
      return
    }
    inFlight.current = true
    setPending(true)
    try {
      if (register) {
        await auth.register({ email: normalized, password })
        setPassword(''); setConfirmation('')
        navigate('/login', { replace: true, state: { registered: true, from } })
      } else {
        await auth.login({ email: normalized, password })
        setPassword('')
        navigate(from, { replace: true })
      }
    } catch (error) {
      setError(authError(error, register ? 'register' : 'login'))
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  return (
    <main className="auth-page">
      <div className="auth-content">
        <Link className="brand" to="/login" aria-label="Hustivoo sign in"><span className="brand-symbol" aria-hidden="true">h</span>Hustivoo</Link>
        <p className="auth-tagline">Your side business. One place.</p>
        <section className="auth-panel" aria-labelledby="auth-title">
          <h1 id="auth-title">{register ? 'Create your account' : 'Welcome back'}</h1>
          <p className="auth-description">{register ? 'A little more space for your next big idea.' : 'Sign in and pick up where you left off.'}</p>
          {!register && state?.registered && <p className="auth-success" role="status">Your account is ready. Sign in to get started.</p>}
          {!register && auth.expired && <p className="auth-notice" role="status">Your session has ended. Sign in to continue.</p>}
          {error && <div ref={errorRef} tabIndex={-1} className="auth-error" role="alert">{error}</div>}
          <form onSubmit={submit} noValidate aria-busy={pending}>
            <FormField label="Email" error={fieldErrors.email}><Input name="email" type="email" autoComplete="email" required value={email} onChange={event => setEmail(event.target.value)} disabled={pending} /></FormField>
            <FormField label="Password" error={fieldErrors.password} hint={register ? 'At least 12 characters. Up to 72 UTF-8 bytes; no special character rules.' : undefined}><Input name="password" type="password" autoComplete={register ? 'new-password' : 'current-password'} required value={password} onChange={event => setPassword(event.target.value)} disabled={pending} /></FormField>
            {register && <FormField label="Confirm password" error={fieldErrors.confirmation}><Input name="confirmation" type="password" autoComplete="new-password" required value={confirmation} onChange={event => setConfirmation(event.target.value)} disabled={pending} /></FormField>}
            <Button type="submit" disabled={pending}>{pending ? (register ? 'Creating account…' : 'Signing in…') : (register ? 'Create account' : 'Sign in')}</Button>
          </form>
          <p className="auth-switch">{register ? 'Already have an account?' : 'New to Hustivoo?'} <Link to={register ? '/login' : '/register'} state={{ from }}>{register ? 'Sign in' : 'Create an account'}</Link></p>
        </section>
        <p className="auth-credit">Developed by Margraf Digital</p>
      </div>
    </main>
  )
}
