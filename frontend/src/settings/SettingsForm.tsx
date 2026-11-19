import { useEffect, useId, useMemo, useRef, useState } from 'react'
import type { CSSProperties, FormEvent, ReactNode } from 'react'
import { ApiError } from '@/api/client'
import { logoUrl, type BusinessSettings, type SettingsInput } from '@/api/settings'
import { countries, industries, legalForms } from '@/business/onboarding-options'
import { Button } from '@/components/ui/button'
import { FormField, Input, Select } from '@/components/ui/forms'
import { InformationPanel } from '@/components/ui/display'
import {
  defaultBrandColor, formatBytes, formatIban, logoProblem, logoTypes, serverErrors, settingsLimits, toForm, toInput,
  validateSettings, type SettingsErrors, type SettingsForm as FormValues,
} from './validation'

interface Props {
  settings: BusinessSettings
  save: (input: SettingsInput) => Promise<BusinessSettings | null>
  uploadLogo: (file: File) => Promise<unknown>
  removeLogo: () => Promise<unknown>
  onDirtyChange?: (dirty: boolean) => void
}

function Section({ id, title, description, children }: { id: string; title: string; description: string; children: ReactNode }) {
  return <section className="settings-section" aria-labelledby={id}>
    <div className="settings-section-intro"><h2 id={id}>{title}</h2><p>{description}</p></div>
    <div className="settings-section-fields">{children}</div>
  </section>
}

/** Business settings form. Members get the same view with every control disabled. */
export function SettingsForm({ settings, save, uploadLogo, removeLogo, onDirtyChange }: Props) {
  const [initial, setInitial] = useState(() => toForm(settings))
  const [form, setForm] = useState(initial)
  const [errors, setErrors] = useState<SettingsErrors>({})
  const [status, setStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle')
  const [logoState, setLogoState] = useState<'idle' | 'uploading' | 'removing'>('idle')
  const [logoMessage, setLogoMessage] = useState('')
  const fileInput = useRef<HTMLInputElement>(null)
  const mounted = useRef(true)
  const idPrefix = useId()
  const editable = settings.editable
  const dirty = useMemo(() => JSON.stringify(form) !== JSON.stringify(initial), [form, initial])
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  useEffect(() => { onDirtyChange?.(dirty) }, [dirty, onDirtyChange])

  const countryOptions = countries.some(([code]) => code === form.country) ? countries : [...countries, [form.country, form.country] as const]
  const color = /^#[0-9A-Fa-f]{6}$/.test(form.brandColor) ? form.brandColor.toUpperCase() : defaultBrandColor

  function update(field: keyof FormValues, value: string) {
    setForm(previous => ({ ...previous, [field]: value }))
    setErrors(previous => ({ ...previous, [field]: undefined }))
    if (status !== 'saving') setStatus('idle')
  }
  function field(name: keyof FormValues, label: string, options: { hint?: string; type?: string; placeholder?: string; autoComplete?: string; list?: string; inputMode?: 'email' | 'tel' | 'url' } = {}) {
    return <FormField label={label} hint={options.hint} error={errors[name]}>
      <Input name={name} id={`${idPrefix}-${name}`} value={form[name]} type={options.type ?? 'text'} placeholder={options.placeholder}
        autoComplete={options.autoComplete ?? 'off'} list={options.list} inputMode={options.inputMode}
        maxLength={settingsLimits[name as keyof typeof settingsLimits] + 20}
        onChange={event => update(name, event.target.value)} />
    </FormField>
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!editable || status === 'saving') return
    const found = validateSettings(form)
    setErrors(found)
    if (Object.values(found).some(Boolean)) { setStatus('error'); focusFirstError(); return }
    setStatus('saving')
    try {
      const saved = await save(toInput(form))
      if (!saved || !mounted.current) return
      const next = toForm(saved)
      setInitial(next); setForm(next); setStatus('saved')
    } catch (error) {
      if (!mounted.current) return
      setErrors(error instanceof ApiError && error.status === 400 ? serverErrors(error.fields, form) : {})
      setStatus('error')
    }
  }
  function focusFirstError() {
    // After React has rendered the error state.
    setTimeout(() => document.querySelector<HTMLElement>('.settings-form [aria-invalid="true"]')?.focus(), 0)
  }
  async function chooseLogo(file: File | undefined) {
    if (!file || logoState !== 'idle') return
    const problem = logoProblem(file)
    setErrors(previous => ({ ...previous, logo: problem ?? undefined }))
    setLogoMessage('')
    if (problem) return
    setLogoState('uploading')
    try {
      await uploadLogo(file)
      if (mounted.current) setLogoMessage('Logo gespeichert.')
    } catch (error) {
      if (mounted.current) setErrors(previous => ({ ...previous, logo: error instanceof ApiError && error.fields.logo ? error.fields.logo : 'Das Logo konnte nicht gespeichert werden. Bitte versuche es erneut.' }))
    } finally {
      if (mounted.current) setLogoState('idle')
      if (fileInput.current) fileInput.current.value = ''
    }
  }
  async function dropLogo() {
    if (logoState !== 'idle') return
    setLogoState('removing'); setLogoMessage(''); setErrors(previous => ({ ...previous, logo: undefined }))
    try {
      await removeLogo()
      if (mounted.current) setLogoMessage('Logo entfernt.')
    } catch {
      if (mounted.current) setErrors(previous => ({ ...previous, logo: 'Das Logo konnte nicht entfernt werden. Bitte versuche es erneut.' }))
    } finally { if (mounted.current) setLogoState('idle') }
  }

  const place = [form.postalCode.trim(), form.city.trim()].filter(Boolean).join(' ')
  const previewAddress = [form.street.trim(), place].filter(Boolean).join(' · ')
  const previewPayment = [form.iban.trim() && `IBAN ${formatIban(form.iban)}`, form.bankName.trim(), form.documentFooter.trim()].filter(Boolean).join(' · ')

  return <form className="settings-form" noValidate onSubmit={event => void submit(event)} aria-busy={status === 'saving'}>
    {!editable && <InformationPanel className="settings-readonly">
      Du bist Mitglied dieses Unternehmens und siehst die Einstellungen schreibgeschützt. Änderungen kann nur der Owner vornehmen.
    </InformationPanel>}
    <fieldset className="settings-fieldset" disabled={!editable || status === 'saving'}>
      <legend className="sr-only">Unternehmenseinstellungen</legend>
      <Section id={`${idPrefix}-company`} title="Unternehmen" description="So erscheint dein Unternehmen in Hustivoo und als Absender auf deinen Dokumenten.">
        {field('name', 'Name des Unternehmens', { autoComplete: 'organization' })}
        <div className="settings-grid">
          {field('legalForm', 'Rechtsform', { list: `${idPrefix}-legal-forms`, placeholder: 'z. B. GmbH' })}
          {field('industry', 'Branche', { list: `${idPrefix}-industries`, placeholder: 'z. B. Beratung' })}
        </div>
        <datalist id={`${idPrefix}-legal-forms`}>{legalForms.map(value => <option key={value} value={value} />)}</datalist>
        <datalist id={`${idPrefix}-industries`}>{industries.map(value => <option key={value} value={value} />)}</datalist>
        <div className="settings-static"><span>Währung</span><strong>{settings.currency}</strong>
          <p className="field-hint">Die Währung ist seit der Einrichtung fest, damit bestehende Angebote und Beträge stimmig bleiben.</p></div>
      </Section>

      <Section id={`${idPrefix}-contact`} title="Kontakt & Adresse" description="Die Adresse steht im Briefkopf. Leere Felder erscheinen nicht im Dokument.">
        {field('street', 'Strasse und Nr.', { autoComplete: 'street-address', placeholder: 'Bahnhofstrasse 1' })}
        <div className="settings-grid settings-grid--place">
          {field('postalCode', 'PLZ', { autoComplete: 'postal-code', placeholder: '8001' })}
          {field('city', 'Ort', { autoComplete: 'address-level2', placeholder: 'Zürich' })}
        </div>
        <FormField label="Land" error={errors.country}>
          <Select name="country" id={`${idPrefix}-country`} value={form.country} onChange={event => update('country', event.target.value)}>
            {countryOptions.map(([code, label]) => <option key={code} value={code}>{label}</option>)}
          </Select>
        </FormField>
        <div className="settings-grid">
          {field('email', 'E-Mail', { type: 'email', inputMode: 'email', autoComplete: 'email', placeholder: 'hallo@beispiel.ch' })}
          {field('phone', 'Telefon', { type: 'tel', inputMode: 'tel', autoComplete: 'tel', placeholder: '+41 44 123 45 67' })}
        </div>
        {field('website', 'Website', { inputMode: 'url', autoComplete: 'url', placeholder: 'www.beispiel.ch' })}
      </Section>

      <Section id={`${idPrefix}-tax`} title="Steuern & Bankverbindung" description="UID und Bankverbindung erscheinen auf jedem Angebot. IBAN und UID werden auf ihre Prüfziffer geprüft.">
        {field('vatNumber', 'UID / MWST-Nummer', { placeholder: 'CHE-123.456.789 MWST', hint: 'Schweizer Unternehmen: CHE-Nummer, optional mit «MWST».' })}
        <div className="settings-grid">
          {field('iban', 'IBAN', { placeholder: 'CH93 0076 2011 6238 5295 7', autoComplete: 'off' })}
          {field('bankName', 'Bank', { placeholder: 'z. B. Zürcher Kantonalbank' })}
        </div>
      </Section>

      <Section id={`${idPrefix}-design`} title="Dokumentdesign" description="Logo, Akzentfarbe und Fusszeile deiner Angebote. Bereits versendete Angebote bleiben unverändert.">
        <div className="form-field">
          <span className="settings-label" id={`${idPrefix}-logo-label`}>Logo</span>
          <div className="settings-logo">
            <div className="settings-logo-preview" aria-hidden={!settings.logo}>
              {settings.logo
                ? <img src={logoUrl(settings.id, settings.logo)} alt={`Logo von ${settings.name}`} width={settings.logo.width} height={settings.logo.height} />
                : <span>Kein Logo</span>}
            </div>
            <div className="settings-logo-actions">
              <input ref={fileInput} id={`${idPrefix}-logo`} className="sr-only" type="file" accept={logoTypes.join(',')}
                aria-labelledby={`${idPrefix}-logo-label`} aria-describedby={`${idPrefix}-logo-hint${errors.logo ? ` ${idPrefix}-logo-error` : ''}`}
                disabled={!editable || logoState !== 'idle'} onChange={event => void chooseLogo(event.target.files?.[0])} />
              {editable && <div className="settings-logo-buttons">
                <Button variant="secondary" disabled={logoState !== 'idle'} onClick={() => fileInput.current?.click()}>
                  {logoState === 'uploading' ? 'Wird hochgeladen…' : settings.logo ? 'Logo ersetzen' : 'Logo hochladen'}
                </Button>
                {settings.logo && <Button variant="tertiary" disabled={logoState !== 'idle'} onClick={() => void dropLogo()}>
                  {logoState === 'removing' ? 'Wird entfernt…' : 'Entfernen'}
                </Button>}
              </div>}
              <p id={`${idPrefix}-logo-hint`} className="field-hint">
                PNG oder JPEG, höchstens 2 MB. Wird für Dokumente optimiert{settings.logo ? ` (aktuell ${settings.logo.width} × ${settings.logo.height} px, ${formatBytes(settings.logo.sizeBytes)})` : ''}.
              </p>
              {errors.logo && <p id={`${idPrefix}-logo-error`} className="field-error" role="alert">{errors.logo}</p>}
              {logoMessage && <p className="settings-inline-success" role="status">{logoMessage}</p>}
            </div>
          </div>
        </div>
        <FormField label="Akzentfarbe" hint="Für «ANGEBOT», Linien und Total. Sie muss auf Weiss gut lesbar sein." error={errors.brandColor}>
          <Input name="brandColor" id={`${idPrefix}-brandColor`} value={form.brandColor} placeholder={`${defaultBrandColor} (Standard)`}
            maxLength={7} autoComplete="off" onChange={event => update('brandColor', event.target.value)} />
        </FormField>
        <div className="settings-color-tools">
          <input type="color" aria-label="Akzentfarbe wählen" value={color.toLowerCase()} onChange={event => update('brandColor', event.target.value.toUpperCase())} />
          {form.brandColor && <Button variant="tertiary" onClick={() => update('brandColor', '')}>Standardfarbe verwenden</Button>}
        </div>
        <FormField label="Fusszeile" hint={`Optional, eine Zeile – z. B. Zahlungsbedingungen. ${form.documentFooter.trim().length}/${settingsLimits.documentFooter} Zeichen.`} error={errors.documentFooter}>
          <Input name="documentFooter" id={`${idPrefix}-documentFooter`} value={form.documentFooter} placeholder="Zahlbar innert 30 Tagen netto."
            maxLength={settingsLimits.documentFooter + 20} onChange={event => update('documentFooter', event.target.value)} />
        </FormField>
        <figure className="settings-preview" aria-label="Vorschau des Briefkopfs">
          <div className="settings-preview-page" style={{ '--document-accent': color } as CSSProperties}>
            <span className="settings-preview-mark" />
            <div className="settings-preview-head">
              <div>
                {settings.logo && <img src={logoUrl(settings.id, settings.logo)} alt="" />}
                <strong>{form.name.trim() || 'Dein Unternehmen'}</strong>
                {previewAddress && <span>{previewAddress}</span>}
              </div>
              <em>Angebot</em>
            </div>
            <div className="settings-preview-lines" aria-hidden="true"><i /><i /><i /></div>
            <div className="settings-preview-total"><span>Total</span><b /></div>
            {/* Same rule as the PDF: payment details and footer note, otherwise the address. */}
            <div className="settings-preview-footer"><span>{previewPayment || previewAddress}</span><span>Seite 1 von 1</span></div>
          </div>
          <figcaption>Vereinfachte Vorschau. Das PDF verwendet nur gespeicherte Angaben.</figcaption>
        </figure>
      </Section>
    </fieldset>

    {editable && <div className="settings-savebar">
      <p role="status" className={status === 'error' ? 'field-error' : status === 'saved' ? 'settings-inline-success' : 'field-hint'}>
        {status === 'saving' ? 'Wird gespeichert…'
          : status === 'saved' ? 'Einstellungen gespeichert.'
          : status === 'error' ? (Object.values(errors).some(Boolean) ? 'Bitte prüfe die markierten Felder.' : 'Die Einstellungen konnten nicht gespeichert werden. Bitte versuche es erneut.')
          : dirty ? 'Du hast ungespeicherte Änderungen.' : 'Alle Änderungen sind gespeichert.'}
      </p>
      <div className="settings-savebar-actions">
        <Button variant="secondary" disabled={!dirty || status === 'saving'} onClick={() => { setForm(initial); setErrors({}); setStatus('idle') }}>Verwerfen</Button>
        <Button type="submit" disabled={!dirty || status === 'saving'}>{status === 'saving' ? 'Wird gespeichert…' : 'Änderungen speichern'}</Button>
      </div>
    </div>}
  </form>
}
