import { useEffect, useState, useSyncExternalStore } from 'react'
import { useBusiness } from '@/business/context'
import * as api from '@/api/settings'
import { settingsStore } from '@/settings/store'
import { SettingsForm } from '@/settings/SettingsForm'
import { Button } from '@/components/ui/button'
import { ErrorState, LoadingState, PageHeader } from '@/components/ui/display'

export function SettingsPage() {
  const { active } = useBusiness()
  // Keyed by business: switching remounts with an empty store, so nothing of the previous business remains.
  return active ? <BusinessSettings key={active.id} businessId={active.id} /> : null
}

function BusinessSettings({ businessId }: { businessId: string }) {
  const business = useBusiness()
  const [store] = useState(() => settingsStore(businessId, api))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [dirty, setDirty] = useState(false)
  useEffect(() => {
    void store.load()
    return () => store.clear()
  }, [store])
  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const { replace, active } = business
  async function save(input: api.SettingsInput) {
    const saved = await store.save(input)
    if (saved && active?.id === saved.id) {
      replace({ ...active, name: saved.name, industry: saved.industry, legalForm: saved.legalForm, country: saved.country, updatedAt: saved.updatedAt })
    }
    return saved
  }
  return <div className="page-content settings-page">
    <PageHeader title="Einstellungen" description="Stammdaten und Branding deines Unternehmens. Neue Angebote übernehmen diese Angaben automatisch." />
    {state.status === 'loading' && <LoadingState label="Einstellungen werden geladen…" />}
    {state.status === 'error' && <ErrorState title="Einstellungen nicht verfügbar" description="Die Unternehmensdaten konnten nicht geladen werden. Bitte prüfe deine Verbindung."
      action={<Button onClick={() => void store.load()}>Erneut laden</Button>} />}
    {state.status === 'ready' && state.settings && <SettingsForm settings={state.settings} save={save}
      uploadLogo={file => store.uploadLogo(file)} removeLogo={() => store.removeLogo()} onDirtyChange={setDirty} />}
  </div>
}
