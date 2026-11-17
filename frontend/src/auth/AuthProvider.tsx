import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import * as api from '@/api/auth'
import { ApiError, clearCsrf, subscribeUnauthorized } from '@/api/client'
import { AuthContext } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<api.AuthUser | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [expired, setExpired] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const hadUser = useRef(false)
  const generation = useRef(0)

  useEffect(() => subscribeUnauthorized(() => {
    generation.current++
    clearCsrf()
    setUser(null)
    setStatus('ready')
    setExpired(hadUser.current)
    hadUser.current = false
  }), [])

  useEffect(() => {
    const controller = new AbortController()
    api.currentUser(controller.signal).then(user => {
      if (controller.signal.aborted) return
      hadUser.current = true
      setUser(user)
      setStatus('ready')
    }).catch(error => {
      if (controller.signal.aborted) return
      setStatus(error instanceof ApiError && error.status === 401 ? 'ready' : 'error')
    })
    return () => controller.abort()
  }, [attempt])

  // Recheck idle sessions when the tab becomes visible. Network errors do not log users out.
  useEffect(() => {
    if (!user) return
    function check() {
      if (document.visibilityState === 'visible') void api.currentUser().catch(() => {})
    }
    document.addEventListener('visibilitychange', check)
    return () => document.removeEventListener('visibilitychange', check)
  }, [user])

  return <AuthContext.Provider value={{ user, status, expired,
    retry() { setStatus('loading'); setAttempt(value => value + 1) },
    async login(credentials) {
      const version = ++generation.current
      const authenticated = await api.login(credentials)
      if (version !== generation.current) return
      hadUser.current = true
      setUser(authenticated)
      setExpired(false)
      setStatus('ready')
    },
    async register(credentials) { await api.register(credentials) },
    async logout() {
      await api.logout()
      generation.current++
      hadUser.current = false
      setUser(null)
      setExpired(false)
      setStatus('ready')
    },
  }}>{children}</AuthContext.Provider>
}
