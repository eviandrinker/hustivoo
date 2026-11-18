export class ApiError extends Error {
  readonly status: number
  readonly fields: Record<string, string>
  constructor(status: number, fields: Record<string, string> = {}) {
    super('Request failed')
    this.status = status
    this.fields = fields
  }
}

let onUnauthorized: (() => void) | undefined
export function subscribeUnauthorized(listener: () => void) {
  onUnauthorized = listener
  return () => { if (onUnauthorized === listener) onUnauthorized = undefined }
}

interface CsrfToken { headerName: string; token: string }
let csrf: CsrfToken | undefined
export function clearCsrf() { csrf = undefined }
export async function refreshCsrf(): Promise<void> {
  csrf = undefined
  const data = await requestJson('/api/v1/auth/csrf')
  if (typeof data !== 'object' || data === null || !('headerName' in data) ||
      data.headerName !== 'X-CSRF-TOKEN' || !('token' in data) || typeof data.token !== 'string' || !data.token) {
    throw new Error('Unexpected response')
  }
  csrf = { headerName: data.headerName, token: data.token }
}

/** All API mutations fetch a fresh session CSRF token; no mutations are retried. */
export async function requestJson(path: string, options: RequestInit = {}): Promise<unknown> {
  if (!path.startsWith('/api/')) throw new Error('Expected a relative API path')
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  const method = (options.method ?? 'GET').toUpperCase()
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    await refreshCsrf()
    if (!csrf) throw new Error('Unable to initialize request')
    headers.set(csrf.headerName, csrf.token)
  }
  const timeout = AbortSignal.timeout(10_000)
  const signal = options.signal ? AbortSignal.any([options.signal, timeout]) : timeout
  const response = await fetch(path, { ...options, headers, signal, credentials: 'same-origin' })
  if (!response.ok) {
    if (response.status === 403) {
      clearCsrf()
      // An expired session can first appear as a CSRF failure. Check identity,
      // but never replay the mutation (it may have side effects).
      if (path !== '/api/v1/auth/me') {
        try { await requestJson('/api/v1/auth/me') } catch { /* 401 notifies the provider. */ }
      }
    }
    if (response.status === 401 && path !== '/api/v1/auth/login') onUnauthorized?.()
    let fields: Record<string, string> = {}
    if (response.status === 400) {
      try {
        const body: unknown = await response.json()
        if (typeof body === 'object' && body !== null && 'fields' in body &&
            typeof body.fields === 'object' && body.fields !== null) {
          fields = Object.fromEntries(Object.entries(body.fields).filter((entry): entry is [string, string] => typeof entry[1] === 'string'))
        }
      } catch { /* Malformed errors remain safe generic errors. */ }
    }
    throw new ApiError(response.status, fields)
  }
  if (response.status === 204) return undefined
  if (!response.headers.get('content-type')?.includes('application/json')) throw new Error('Unexpected response')
  return response.json()
}
