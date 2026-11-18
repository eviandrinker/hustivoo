import type { Customer, CustomerInput, CustomerFilters } from '../api/customers'
interface CustomerState { rows: Customer[]; status: 'loading' | 'ready' | 'error'; search: string; includeArchived: boolean; filters: CustomerFilters }
export function customerStore(businessId: string, api: {
  listCustomers: (businessId: string, search: string, archived: boolean, filters?: CustomerFilters) => Promise<Customer[]>
  saveCustomer: (businessId: string, input: CustomerInput, id?: string) => Promise<Customer>
  archiveCustomer: (businessId: string, id: string, archived: boolean) => Promise<Customer>
}) {
  let state: CustomerState = { rows: [], status: 'loading', search: '', includeArchived: false, filters: {} }
  let epoch = 0
  let lifecycle = 0
  const listeners = new Set<() => void>()
  function update(next: CustomerState) { state = next; listeners.forEach(listener => listener()) }
  async function load(search = state.search, includeArchived = state.includeArchived, filters = state.filters) {
    const version = ++epoch
    update({ rows: [], status: 'loading', search, includeArchived, filters })
    try {
      const rows = await api.listCustomers(businessId, search, includeArchived, filters)
      if (version === epoch) update({ ...state, rows, status: 'ready' })
    } catch { if (version === epoch) update({ ...state, rows: [], status: 'error' }) }
  }
  return {
    getSnapshot: () => state,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
    load,
    clear() { epoch++; lifecycle++; update({ rows: [], status: 'loading', search: '', includeArchived: false, filters: {} }) },
    async save(input: CustomerInput, id?: string) {
      const current = lifecycle
      await api.saveCustomer(businessId, input, id)
      if (current !== lifecycle) return
      await load()
    },
    async archive(id: string, archived: boolean) {
      const current = lifecycle
      await api.archiveCustomer(businessId, id, archived)
      if (current !== lifecycle) return
      await load()
    },
  }
}
