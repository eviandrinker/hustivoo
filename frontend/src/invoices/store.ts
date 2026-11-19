import type { Invoice, InvoiceFilters, InvoiceSummary } from '../api/invoices'
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

export interface InvoiceListState { rows: InvoiceSummary[]; status: 'loading' | 'ready' | 'error'; filters: InvoiceFilters }
const initialFilters: InvoiceFilters = { search: '', status: '' }

/** Business-scoped list. `clear()` (business switch, logout, unmount) drops rows and ignores pending reads. */
export function invoiceListStore(businessId: string, api: { listInvoices: (businessId: string, filters: InvoiceFilters) => Promise<InvoiceSummary[]> }) {
  const store = emitter<InvoiceListState>({ rows: [], status: 'loading', filters: { ...initialFilters } })
  let epoch = 0
  return {
    getSnapshot: store.get,
    subscribe: store.subscribe,
    async load(filters = store.get().filters) {
      const version = ++epoch
      store.set({ rows: [], status: 'loading', filters })
      try {
        const rows = await api.listInvoices(businessId, filters)
        if (version === epoch) store.set({ ...store.get(), rows, status: 'ready' })
      } catch {
        if (version === epoch) store.set({ ...store.get(), rows: [], status: 'error' })
      }
    },
    clear() { epoch++; store.set({ rows: [], status: 'loading', filters: { ...initialFilters } }) },
  }
}

export interface InvoiceDetailState { status: 'loading' | 'ready' | 'error'; invoice: Invoice | null }

/** One invoice. Mutations apply their server response only while the store is still current. */
export function invoiceDetailStore(businessId: string, id: string, api: { getInvoice: (businessId: string, id: string) => Promise<Invoice> }) {
  const store = emitter<InvoiceDetailState>({ status: 'loading', invoice: null })
  let epoch = 0
  let lifecycle = 0
  return {
    getSnapshot: store.get,
    subscribe: store.subscribe,
    async load() {
      const version = ++epoch
      store.set({ status: 'loading', invoice: null })
      try {
        const invoice = await api.getInvoice(businessId, id)
        if (version === epoch) store.set({ status: 'ready', invoice })
      } catch {
        if (version === epoch) store.set({ status: 'error', invoice: null })
      }
    },
    /** Runs a mutation returning the updated invoice; resolves false if the store was cleared meanwhile. */
    async apply(operation: () => Promise<Invoice>) {
      const current = lifecycle
      const invoice = await operation()
      if (current !== lifecycle) return false
      epoch++
      store.set({ status: 'ready', invoice })
      return true
    },
    async run(operation: () => Promise<unknown>) {
      const current = lifecycle
      await operation()
      return current === lifecycle
    },
    clear() { epoch++; lifecycle++; store.set({ status: 'loading', invoice: null }) },
  }
}

export interface InvoiceEditorState { status: 'loading' | 'ready' | 'error'; invoice: Invoice | null; customers: Customer[]; services: CatalogService[] }

export function invoiceEditorStore(businessId: string, id: string | undefined, api: {
  getInvoice: (businessId: string, id: string) => Promise<Invoice>
  listCustomers: (businessId: string) => Promise<Customer[]>
  listServices: (businessId: string, filters: ServiceFilters) => Promise<CatalogService[]>
}) {
  const empty: InvoiceEditorState = { status: 'loading', invoice: null, customers: [], services: [] }
  const store = emitter<InvoiceEditorState>(empty)
  let epoch = 0
  let lifecycle = 0
  return {
    getSnapshot: store.get,
    subscribe: store.subscribe,
    async load() {
      const version = ++epoch
      store.set(empty)
      try {
        const [invoice, customers, services] = await Promise.all([
          id ? api.getInvoice(businessId, id) : Promise.resolve(null),
          api.listCustomers(businessId),
          api.listServices(businessId, { search: '', category: '', active: true, sort: 'NAME_ASC' }),
        ])
        if (version === epoch) store.set({ status: 'ready', invoice, customers, services })
      } catch {
        if (version === epoch) store.set({ ...empty, status: 'error' })
      }
    },
    async save(operation: () => Promise<Invoice>) {
      const current = lifecycle
      const invoice = await operation()
      return current === lifecycle ? invoice : null
    },
    clear() { epoch++; lifecycle++; store.set(empty) },
  }
}
