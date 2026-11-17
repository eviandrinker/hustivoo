import { ApiError } from '@/api/client'
export function authError(error: unknown, action: 'login' | 'register' | 'logout') {
  if (error instanceof ApiError) {
    if (error.status === 401) return action === 'login' ? 'That email and password don’t match. Please try again.' : 'Your session has expired. Please sign in again.'
    if (error.status === 409) return 'An account with this email already exists. Try signing in.'
    if (error.status === 400) return 'Please check your email and password requirements.'
    if (error.status === 403) return 'We couldn’t verify this request. Please try again to refresh your security token.'
  }
  return action === 'logout' ? 'We couldn’t confirm sign-out. Please try again.' : 'We couldn’t connect or received an unexpected response. Please try again.'
}
