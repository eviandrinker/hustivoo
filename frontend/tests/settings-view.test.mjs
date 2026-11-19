import test from 'node:test'
import assert from 'node:assert/strict'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { renderModule } from './helpers/render-module.mjs'
import { settings } from './helpers/settings-fixture.mjs'

const { SettingsForm } = await import(await renderModule('settings/SettingsForm.tsx'))
const noop = async () => null
const render = (overrides = {}) => renderToStaticMarkup(createElement(SettingsForm, { settings: { ...settings, ...overrides }, save: noop, uploadLogo: noop, removeLogo: noop }))

test('owners see four sections with every field, saved values and save controls', () => {
  const html = render()
  for (const heading of ['Unternehmen', 'Kontakt &amp; Adresse', 'Steuern &amp; Bankverbindung', 'Dokumentdesign']) assert.match(html, new RegExp(`<h2[^>]*>${heading}</h2>`))
  for (const name of ['name', 'legalForm', 'industry', 'street', 'postalCode', 'city', 'country', 'email', 'phone', 'website', 'vatNumber', 'iban', 'bankName', 'brandColor', 'documentFooter']) {
    assert.ok(html.includes(`name="${name}"`), name)
  }
  assert.match(html, /value="Studio Nord GmbH"/)
  assert.match(html, /value="CH93 0076 2011 6238 5295 7"/)
  assert.match(html, /value="CHE-116.281.710 MWST"/)
  assert.match(html, /<option value="CH" selected="">Schweiz<\/option>/)
  assert.match(html, /<strong>CHF<\/strong>/)                         // currency shown read-only
  assert.doesNotMatch(html, /name="currency"/)
  assert.match(html, /Änderungen speichern/); assert.match(html, /Verwerfen/)
  assert.match(html, /Alle Änderungen sind gespeichert/)
  assert.doesNotMatch(html, /<fieldset[^>]*disabled/)
  assert.doesNotMatch(html, /schreibgeschützt/)
  assert.doesNotMatch(html, /role="alert"/)
})

test('logo upload offers preview, replace and remove; without a logo only upload', () => {
  const html = render()
  assert.match(html, new RegExp(`<img src="/api/businesses/${settings.id}/settings/logo\\?v=${settings.logo.id}" alt="Logo von Studio Nord GmbH"`))
  assert.match(html, /Logo ersetzen/); assert.match(html, /Entfernen/)
  assert.match(html, /accept="image\/png,image\/jpeg"/)
  assert.match(html, /600 × 200 px, 23 KB/)
  const empty = render({ logo: null })
  assert.match(empty, /Kein Logo/); assert.match(empty, /Logo hochladen/)
  assert.doesNotMatch(empty, /<img/); assert.doesNotMatch(empty, />Entfernen</)
})

test('the document preview uses the accent colour and only stored details', () => {
  const html = render()
  assert.match(html, /--document-accent:#1F3A5F/)
  assert.match(html, /Seestrasse 12 · 8002 Zürich/)
  assert.match(html, /IBAN CH93 0076 2011 6238 5295 7 · Zürcher Kantonalbank/)
  const bare = render({ street: null, postalCode: null, city: null, iban: null, bankName: null, brandColor: null, logo: null })
  assert.match(bare, /--document-accent:#245747/)                     // Hustivoo default
  assert.doesNotMatch(bare, /IBAN CH/)
  assert.match(bare, /<div class="settings-preview-footer"><span><\/span><span>Seite 1 von 1<\/span>/)
  assert.match(render({ iban: null, bankName: null }), /<div class="settings-preview-footer"><span>Seestrasse 12 · 8002 Zürich<\/span>/)
})

test('members see the same data read-only without any change controls', () => {
  const html = render({ role: 'MEMBER', editable: false })
  assert.match(html, /<fieldset class="settings-fieldset" disabled="">/)
  assert.match(html, /schreibgeschützt/); assert.match(html, /nur der Owner/)
  assert.match(html, /value="Studio Nord GmbH"/); assert.match(html, /value="CH93 0076 2011 6238 5295 7"/)
  assert.doesNotMatch(html, /Änderungen speichern|Verwerfen|Logo ersetzen|Logo hochladen|>Entfernen</)
  assert.match(html, /<img src=/)                                     // the logo stays visible
})

test('an unknown country keeps its own option instead of silently switching', () => {
  assert.match(render({ country: 'JP' }), /<option value="JP" selected="">JP<\/option>/)
})
