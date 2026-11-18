import type { Quote, QuoteFilters, QuoteSummary } from '../api/quotes'
import type { Customer } from '../api/customers'
import type { CatalogService, ServiceFilters } from '../api/services'

type Listener = () => void
function emitter<T>(initial: T) {
  let state = initial
  const listeners = new Set<Listener>()
  return {
    get: () => state,
    set(next: T) { state = next; listeners.forEach(listener => listener()) },
    subscribe(listener: Listener) { listeners.add(listener); return () => { listeners.delete(listener) } },
  }
}

export interface QuoteListState { rows: QuoteSummary[]; status: 'loading' | 'ready' | 'error'; filters: QuoteFilters }
const initialFilters: QuoteFilters = { search: '', status: '' }

/** Business-scoped list. `clear()` (business switch, logout, unmount) drops rows and ignores pending reads. */
export function quoteListStore(businessId: string, api: { listQuotes: (businessId: string, filters: QuoteFilters) => Promise<QuoteSummary[]> }) {
  const store = emitter<QuoteListState>({ rows: [], status: 'loading', filters: { ...initialFilters } })
  let epoch = 0
  return {
    getSnapshot: store.get,
    subscribe: store.subscribe,
    async load(filters = store.get().filters) {
      const version = ++epoch
      store.set({ rows: [], status: 'loading', filters })
      try {
        const rows = await api.listQuotes(businessId, filters)
        if (version === epoch) store.set({ ...store.get(), rows, status: 'ready' })
      } catch {
        if (version === epoch) store.set({ ...store.get(), rows: [], status: 'error' })
      }
    },
    clear() { epoch++; store.set({ rows: [], status: 'loading', filters: { ...initialFilters } }) },
  }
}

export interface QuoteDetailState { status: 'loading' | 'ready' | 'error'; quote: Quote | null }

/** One quote. Mutations apply their server response only while the store is still current. */
export function quoteDetailStore(businessId: string, id: string, api: { getQuote: (businessId: string, id: string) => Promise<Quote> }) {
  const store = emitter<QuoteDetailState>({ status: 'loading', quote: null })
  let epoch = 0
  let lifecycle = 0
  return {
    getSnapshot: store.get,
    subscribe: store.subscribe,
    async load() {
      const version = ++epoch
      store.set({ status: 'loading', quote: null })
      try {
        const quote = await api.getQuote(businessId, id)
        if (version === epoch) store.set({ status: 'ready', quote })
      } catch {
        if (version === epoch) store.set({ status: 'error', quote: null })
      }
    },
    /** Runs a mutation returning the updated quote; resolves false if the store was cleared meanwhile. */
    async apply(operation: () => Promise<Quote>) {
      const current = lifecycle
      const quote = await operation()
      if (current !== lifecycle) return false
      epoch++
      store.set({ status: 'ready', quote })
      return true
    },
    async run(operation: () => Promise<unknown>) {
      const current = lifecycle
      await operation()
      return current === lifecycle
    },
    clear() { epoch++; lifecycle++; store.set({ status: 'loading', quote: null }) },
  }
}

export interface QuoteEditorState {
  status: 'loading' | 'ready' | 'error'
  quote: Quote | null
  customers: Customer[]
  services: CatalogService[]
}

/** Loads everything the editor may reference: the draft (if any) plus this business's customers and active services. */
export function quoteEditorStore(businessId: string, id: string | undefined, api: {
  getQuote: (businessId: string, id: string) => Promise<Quote>
  listCustomers: (businessId: string) => Promise<Customer[]>
  listServices: (businessId: string, filters: ServiceFilters) => Promise<CatalogService[]>
}) {
  const empty: QuoteEditorState = { status: 'loading', quote: null, customers: [], services: [] }
  const store = emitter<QuoteEditorState>(empty)
  let epoch = 0
  let lifecycle = 0
  return {
    getSnapshot: store.get,
    subscribe: store.subscribe,
    async load() {
      const version = ++epoch
      store.set(empty)
      try {
        const [quote, customers, services] = await Promise.all([
          id ? api.getQuote(businessId, id) : Promise.resolve(null),
          api.listCustomers(businessId),
          api.listServices(businessId, { search: '', category: '', active: true, sort: 'NAME_ASC' }),
        ])
        if (version === epoch) store.set({ status: 'ready', quote, customers, services })
      } catch {
        if (version === epoch) store.set({ ...empty, status: 'error' })
      }
    },
    async save(operation: () => Promise<Quote>) {
      const current = lifecycle
      const quote = await operation()
      return current === lifecycle ? quote : null
    },
    clear() { epoch++; lifecycle++; store.set(empty) },
  }
}
