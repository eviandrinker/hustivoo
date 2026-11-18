import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useBusiness } from '@/business/context'
import { validateBusiness } from '@/business/validation'
import type { CreateBusiness } from '@/api/business'
import { ApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { ArrowRight } from 'lucide-react'
import { industries, legalForms, countries, currencies, optionalSelection } from '@/business/onboarding-options'
import { FormField, Input, Select } from '@/components/ui/forms'
import { LogoutButton } from '@/components/LogoutButton'

export function OnboardingPage() {
  const business = useBusiness()
  const [form, setForm] = useState<CreateBusiness>({ name: '', industry: '', legalForm: '', country: 'CH', currency: 'CHF' })
  const [industryChoice, setIndustryChoice] = useState('')
  const [legalChoice, setLegalChoice] = useState('')
  const [invalidRequest, setInvalidRequest] = useState(false)
  const formRef = useRef<HTMLFormElement>(null)
  const [errors, setErrors] = useState<ReturnType<typeof validateBusiness>>({})
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const busy = useRef(false)
  const alert = useRef<HTMLDivElement>(null)
  useEffect(() => { document.title = 'Business einrichten · Hustivoo' }, [])
  useEffect(() => { if (error) alert.current?.focus() }, [error])
  function field(key: keyof CreateBusiness, value: string) {
    setForm(previous => ({ ...previous, [key]: value }))
    setErrors(previous => ({ ...previous, [key]: undefined }))
    setInvalidRequest(false)
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy.current) return
    const input = { ...form, name: form.name.trim(), industry: optionalSelection(industryChoice, form.industry ?? ''),
      legalForm: optionalSelection(legalChoice, form.legalForm ?? ''), country: form.country.trim().toUpperCase(), currency: form.currency.trim().toUpperCase() }
    const validation = validateBusiness(input)
    setErrors(validation); setError(''); setInvalidRequest(false)
    if (Object.keys(validation).length) {
      const key = Object.keys(validation)[0]
      requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>(`[name="${key}"]`)?.focus())
      return
    }
    busy.current = true; setPending(true)
    try { await business.create(input) }
    catch (error) {
      if (error instanceof ApiError && error.status === 400) {
        setInvalidRequest(true)
      } else {
        setError(error instanceof ApiError && error.status === 403
          ? 'Die Sicherheitsprüfung ist fehlgeschlagen. Bitte versuche es erneut.'
          : 'Dein Business konnte nicht bestätigt werden. Bitte prüfe deine Verbindung und versuche es später erneut.')
      }
    } finally { busy.current = false; setPending(false) }
  }
  return <main className="auth-page onboarding-page"><div className="onboarding-content">
    <header className="onboarding-brand"><span className="brand"><span className="brand-symbol" aria-hidden="true">h</span>Hustivoo</span></header>
    <section className="auth-panel onboarding-panel" aria-labelledby="onboarding-title">
      <p className="onboarding-step">Schritt 2 von 2<span className="onboarding-step-track" aria-hidden="true"><span /><span /></span></p>
      <h1 id="onboarding-title">Dein Business beginnt hier.</h1><p className="auth-description">Erzähl uns kurz etwas über dein Business.</p>
      {error && <div ref={alert} tabIndex={-1} className="auth-error" role="alert">{error}</div>}
      <form ref={formRef} noValidate onSubmit={submit} aria-busy={pending}>
        <FormField label="Unternehmensname" error={errors.name}><Input name="name" autoComplete="organization" placeholder="Studio Müller" value={form.name} onChange={e => field('name', e.target.value)} required disabled={pending} /></FormField>
        <div className="onboarding-field-group">
          <FormField label="Branche" error={industryChoice !== 'other' ? errors.industry : undefined}>
            <Select name={industryChoice === 'other' ? 'industryChoice' : 'industry'} value={industryChoice} onChange={e => { setIndustryChoice(e.target.value); field('industry', '') }} disabled={pending}>
              <option value="">Bitte auswählen (optional)</option>{industries.map(value => <option key={value}>{value}</option>)}<option value="other">Sonstiges</option>
            </Select>
          </FormField>
          {industryChoice === 'other' && <FormField label="Deine Branche (optional)" error={errors.industry}><Input name="industry" value={form.industry ?? ''} placeholder="Wie würdest du deine Tätigkeit beschreiben?" onChange={e => field('industry', e.target.value)} disabled={pending} /></FormField>}
        </div>
        <div className="onboarding-field-group">
          <FormField label="Unternehmensform" error={legalChoice !== 'other' ? errors.legalForm : undefined}>
            <Select name={legalChoice === 'other' ? 'legalChoice' : 'legalForm'} value={legalChoice} onChange={e => { setLegalChoice(e.target.value); field('legalForm', '') }} disabled={pending}>
              <option value="">Noch nicht festgelegt (optional)</option>{legalForms.map(value => <option key={value}>{value}</option>)}<option value="other">Sonstiges</option>
            </Select>
          </FormField>
          {legalChoice === 'other' && <FormField label="Deine Unternehmensform (optional)" error={errors.legalForm}><Input name="legalForm" value={form.legalForm ?? ''} placeholder="Unternehmensform angeben" onChange={e => field('legalForm', e.target.value)} disabled={pending} /></FormField>}
        </div>
        <div className="form-grid onboarding-location">
          <FormField label="Land" error={errors.country}><Select name="country" autoComplete="country" value={form.country} onChange={e => field('country', e.target.value)} required disabled={pending}>{countries.map(([code, label]) => <option key={code} value={code}>{label}</option>)}</Select></FormField>
          <FormField label="Währung" error={errors.currency}><Select name="currency" value={form.currency} onChange={e => field('currency', e.target.value)} required disabled={pending}>{currencies.map(([code, label]) => <option key={code} value={code}>{label}</option>)}</Select></FormField>
        </div>
        {invalidRequest && <p className="field-error" role="alert">Bitte prüfe deine Angaben. Die Eingaben konnten nicht übernommen werden.</p>}
        <Button type="submit" disabled={pending}>{pending ? 'Business wird erstellt…' : <>Loslegen<ArrowRight size={16} aria-hidden="true" /></>}</Button>
      </form>
    </section><footer className="onboarding-footer"><span>Entwickelt von Margraf Digital</span><LogoutButton /></footer>
  </div></main>
}
