import type { CatalogService, ServiceInput, ServiceFilters } from '../api/services'
import { defaultServiceFilters } from '../api/services'
interface ServiceState { rows: CatalogService[]; status: 'loading' | 'ready' | 'error'; filters: ServiceFilters }
export function serviceStore(businessId: string, api: {
  listServices: (businessId: string, filters: ServiceFilters) => Promise<CatalogService[]>
  saveService: (businessId: string, input: ServiceInput, id?: string) => Promise<CatalogService>
  archiveService: (businessId: string, id: string, archived: boolean) => Promise<CatalogService>
}) {
  let state: ServiceState = { rows: [], status: 'loading', filters: { ...defaultServiceFilters } }
  let epoch = 0
  let lifecycle = 0
  const listeners = new Set<() => void>()
  function update(next: ServiceState) { state = next; listeners.forEach(listener => listener()) }
  async function load(filters = state.filters) {
    const version = ++epoch
    update({ rows: [], status: 'loading', filters })
    try {
      const rows = await api.listServices(businessId, filters)
      if (version === epoch) update({ ...state, rows, status: 'ready' })
    } catch { if (version === epoch) update({ ...state, rows: [], status: 'error' }) }
  }
  return {
    getSnapshot: () => state,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
    load,
    clear() { epoch++; lifecycle++; update({ rows: [], status: 'loading', filters: { ...defaultServiceFilters } }) },
    async save(input: ServiceInput, id?: string) {
      const current = lifecycle
      await api.saveService(businessId, input, id)
      if (current !== lifecycle) return
      await load()
    },
    async archive(id: string, archived: boolean) {
      const current = lifecycle
      await api.archiveService(businessId, id, archived)
      if (current !== lifecycle) return
      await load()
    },
  }
}
