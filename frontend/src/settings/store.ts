import type { BusinessSettings, SettingsInput } from '../api/settings'

export interface SettingsState { status: 'loading' | 'ready' | 'error'; settings: BusinessSettings | null }

/**
 * Settings of one business. `clear()` (business switch, logout, unmount) drops the data and makes every
 * pending read or mutation a no-op, so a late response can never show another business's settings.
 */
export function settingsStore(businessId: string, api: {
  getSettings: (businessId: string) => Promise<BusinessSettings>
  updateSettings: (businessId: string, input: SettingsInput) => Promise<BusinessSettings>
  uploadLogo: (businessId: string, file: Blob) => Promise<BusinessSettings>
  removeLogo: (businessId: string) => Promise<BusinessSettings>
}) {
  let state: SettingsState = { status: 'loading', settings: null }
  let epoch = 0
  let lifecycle = 0
  const listeners = new Set<() => void>()
  function set(next: SettingsState) { state = next; listeners.forEach(listener => listener()) }
  /** Applies a mutation's response only while the store is current and the response belongs to this business. */
  async function apply(operation: () => Promise<BusinessSettings>) {
    const current = lifecycle
    const settings = await operation()
    if (current !== lifecycle || settings.id !== businessId) return null
    epoch++
    set({ status: 'ready', settings })
    return settings
  }
  return {
    getSnapshot: () => state,
    subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
    async load() {
      const version = ++epoch
      set({ status: 'loading', settings: null })
      try {
        const settings = await api.getSettings(businessId)
        if (version !== epoch) return
        set(settings.id === businessId ? { status: 'ready', settings } : { status: 'error', settings: null })
      } catch {
        if (version === epoch) set({ status: 'error', settings: null })
      }
    },
    save: (input: SettingsInput) => apply(() => api.updateSettings(businessId, input)),
    uploadLogo: (file: Blob) => apply(() => api.uploadLogo(businessId, file)),
    removeLogo: () => apply(() => api.removeLogo(businessId)),
    clear() { epoch++; lifecycle++; set({ status: 'loading', settings: null }) },
  }
}
export type SettingsStore = ReturnType<typeof settingsStore>
