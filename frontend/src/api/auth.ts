import { requestJson, refreshCsrf, clearCsrf } from './client'

export interface AuthUser { id: string; email: string }
export interface Credentials { email: string; password: string }
function userResponse(data: unknown): AuthUser {
  if (typeof data !== 'object' || data === null || !('id' in data) || typeof data.id !== 'string' ||
      !data.id || !('email' in data) || typeof data.email !== 'string' || !data.email) {
    throw new Error('Unexpected response')
  }
  return { id: data.id, email: data.email }
}
export async function currentUser(signal?: AbortSignal) {
  return userResponse(await requestJson('/api/v1/auth/me', { signal }))
}
function credentialsRequest(path: string, credentials: Credentials) {
  return requestJson(path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(credentials) })
}
// A refresh failure must not undo an already completed login or logout.
async function refreshAfterSessionChange() {
  clearCsrf()
  try { await refreshCsrf() } catch { clearCsrf() }
}
export async function login(credentials: Credentials) {
  const user = userResponse(await credentialsRequest('/api/v1/auth/login', credentials))
  await refreshAfterSessionChange()
  return user
}
export async function register(credentials: Credentials) {
  return userResponse(await credentialsRequest('/api/v1/auth/register', credentials))
}
export async function logout() {
  await requestJson('/api/v1/auth/logout', { method: 'POST' })
  await refreshAfterSessionChange()
}
