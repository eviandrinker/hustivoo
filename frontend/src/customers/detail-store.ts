import type { Customer, Contact, CustomerNote, CustomerActivity } from '../api/customers'
export interface CustomerDetail { customer: Customer; contacts: Contact[]; notes: CustomerNote[]; activities: CustomerActivity[] }
interface DetailState { status: 'loading' | 'ready' | 'error'; data: CustomerDetail | null }
export function customerDetailStore(businessId: string, customerId: string, api: {
  getCustomer: (business: string, customer: string) => Promise<Customer>
  listContacts: (business: string, customer: string) => Promise<Contact[]>
  listNotes: (business: string, customer: string) => Promise<CustomerNote[]>
  listActivities: (business: string, customer: string) => Promise<CustomerActivity[]>
}) {
  let state: DetailState = { status: 'loading', data: null }
  let generation = 0
  let lifecycle = 0
  const listeners = new Set<() => void>()
  function set(next: DetailState) { state = next; listeners.forEach(listener => listener()) }
  async function load() {
    const version = ++generation
    set({ status: 'loading', data: null })
    try {
      const [customer, contacts, notes, activities] = await Promise.all([api.getCustomer(businessId, customerId), api.listContacts(businessId, customerId), api.listNotes(businessId, customerId), api.listActivities(businessId, customerId)])
      if (version === generation) set({ status: 'ready', data: { customer, contacts, notes, activities } })
    } catch { if (version === generation) set({ status: 'error', data: null }) }
  }
  return {
    getSnapshot: () => state,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
    load,
    clear() { generation++; lifecycle++; set({ status: 'loading', data: null }) },
    async mutate(operation: () => Promise<unknown>) {
      const version = lifecycle
      await operation()
      if (version !== lifecycle) return false
      await load()
      return version === lifecycle
    },
  }
}
