/** Request generation and lifecycle are separate so a late mutation cannot restore a signed-out workspace. */
export function expenseStore<T>(loadData: () => Promise<T>) {
  let state: { status: 'loading' | 'ready' | 'error'; data: T | null } = { status: 'loading', data: null }
  let epoch = 0; let lifecycle = 0
  const listeners = new Set<() => void>()
  function set(next: typeof state) { state = next; listeners.forEach(fn => fn()) }
  async function load() {
    const current = ++epoch; set({ status: 'loading', data: null })
    try { const data = await loadData(); if (current === epoch) set({ status: 'ready', data }) }
    catch { if (current === epoch) set({ status: 'error', data: null }) }
  }
  return { getSnapshot: () => state, subscribe(fn: () => void) { listeners.add(fn); return () => { listeners.delete(fn) } }, load,
    clear() { epoch++; lifecycle++; set({ status: 'loading', data: null }) },
    async run<R>(operation: () => Promise<R>): Promise<R | undefined> { const current = lifecycle; const result = await operation(); return current === lifecycle ? result : undefined },
  }
}
