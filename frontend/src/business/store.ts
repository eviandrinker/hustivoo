import type { Business, CreateBusiness } from '../api/business'

export interface BusinessState {
  businesses: Business[]
  active: Business | null
  status: 'loading' | 'ready' | 'error'
}
export function businessDestination(state: BusinessState): 'loading' | 'error' | '/onboarding' | '/dashboard' {
  return state.status === 'ready' ? (state.businesses.length ? '/dashboard' : '/onboarding') : state.status
}
export function createBusinessStore(api: {
  listBusinesses: () => Promise<Business[]>
  createBusiness: (input: CreateBusiness) => Promise<Business>
}) {
  let state: BusinessState = { businesses: [], active: null, status: 'loading' }
  let epoch = 0
  const listeners = new Set<() => void>()
  function update(next: BusinessState) { state = next; listeners.forEach(listener => listener()) }
  return {
    getSnapshot: () => state,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
    clear() { epoch++; update({ businesses: [], active: null, status: 'loading' }) },
    async refresh() {
      const request = ++epoch
      update({ ...state, status: 'loading' })
      try {
        const businesses = await api.listBusinesses()
        if (request !== epoch) return
        const active = businesses.find(item => item.id === state.active?.id) ?? businesses[0] ?? null
        update({ businesses, active, status: 'ready' })
      } catch {
        if (request === epoch) update({ businesses: [], active: null, status: 'error' })
      }
    },
    select(id: string) {
      const active = state.businesses.find(item => item.id === id)
      if (!active || state.status !== 'ready') throw new Error('Business unavailable')
      update({ ...state, active })
    },
    async create(input: CreateBusiness) {
      const request = ++epoch
      const business = await api.createBusiness(input)
      if (request !== epoch) throw new Error('Session changed')
      update({ businesses: [...state.businesses.filter(item => item.id !== business.id), business], active: business, status: 'ready' })
      return business
    },
  }
}
export type BusinessStore = ReturnType<typeof createBusinessStore>
