import { createContext, useContext, useSyncExternalStore } from 'react'
import type { BusinessStore } from './store'
export const BusinessContext = createContext<BusinessStore | null>(null)
export function useBusiness() {
  const store = useContext(BusinessContext)
  if (!store) throw new Error('BusinessProvider required')
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  return { ...state, refresh: store.refresh, select: store.select, create: store.create }
}
