import { createContext, useContext } from 'react'
import type { AuthUser, Credentials } from '@/api/auth'

export interface AuthState {
  user: AuthUser | null
  status: 'loading' | 'ready' | 'error'
  expired: boolean
  retry: () => void
  login: (credentials: Credentials) => Promise<void>
  register: (credentials: Credentials) => Promise<void>
  logout: () => Promise<void>
}
export const AuthContext = createContext<AuthState | null>(null)
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('AuthProvider is required')
  return context
}
