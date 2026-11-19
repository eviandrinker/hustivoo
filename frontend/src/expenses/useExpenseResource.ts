import { useEffect, useMemo, useSyncExternalStore } from 'react'
import { expenseStore } from './store'
export function useExpenseResource<T>(load: () => Promise<T>) {
  const store = useMemo(() => expenseStore(load), [load])
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  useEffect(() => { void store.load(); return () => store.clear() }, [store])
  return { ...state, store }
}
