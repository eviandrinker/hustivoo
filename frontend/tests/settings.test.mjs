import test from 'node:test'
import assert from 'node:assert/strict'
import { renderModule } from './helpers/render-module.mjs'

const api = await import(await renderModule('api/settings.ts'))
const validation = await import(await renderModule('settings/validation.ts'))
const { settingsStore } = await import(await renderModule('settings/store.ts'))
const { createBusinessStore } = await import(await renderModule('business/store.ts'))
import { LOGO, settings } from './helpers/settings-fixture.mjs'

const A = '11111111-1111-4111-8111-111111111111'
const B = '22222222-2222-4222-8222-222222222222'
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const csrf = { headerName: 'X-CSRF-TOKEN', token: 'fresh-token' }
const form = overrides => ({ ...validation.toForm(settings), ...overrides })

// ------------------------------------------------------------------ API

test('settings API scopes requests to the business and sends CSRF tokens for changes', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options })
    return path.endsWith('/csrf') ? json(csrf) : json(settings)
  })
  assert.equal((await api.getSettings(A)).iban, 'CH9300762011623852957')
  await api.updateSettings(A, validation.toInput(form()))
  const file = new Blob([new Uint8Array([0x89, 0x50])], { type: 'image/png' })
  await api.uploadLogo(A, file)
  await api.removeLogo(A)
  const writes = calls.filter(call => !call.path.endsWith('/csrf'))
  assert.deepEqual(writes.map(call => [call.options.method ?? 'GET', call.path]), [
    ['GET', `/api/businesses/${A}/settings`], ['PUT', `/api/businesses/${A}/settings`],
    ['PUT', `/api/businesses/${A}/settings/logo`], ['DELETE', `/api/businesses/${A}/settings/logo`]])
  for (const call of writes.slice(1)) assert.equal(new Headers(call.options.headers).get('X-CSRF-TOKEN'), 'fresh-token')
  assert.equal(new Headers(writes[2].options.headers).get('Content-Type'), 'image/png')
  assert.equal(writes[2].options.body, file)
  const body = JSON.parse(writes[1].options.body)
  assert.equal(body.iban, 'CH9300762011623852957')
  assert.equal(body.currency, undefined)  // the currency is not editable
  assert.equal(api.logoUrl(A, settings.logo), `/api/businesses/${A}/settings/logo?v=${LOGO}`)
})

test('settings responses are validated and a member can never be editable', () => {
  assert.equal(api.parseSettings({ ...settings, role: 'MEMBER', editable: true }).editable, false)
  assert.equal(api.parseSettings({ ...settings, logo: null }).logo, null)
  for (const broken of [{ ...settings, id: 'x' }, { ...settings, role: 'ADMIN' }, { ...settings, iban: 42 }, { ...settings, logo: { id: LOGO } }, { ...settings, name: ' ' }, null]) {
    assert.throws(() => api.parseSettings(broken))
  }
})

test('server field errors reach the form as ApiError fields', async t => {
  t.mock.method(globalThis, 'fetch', async path => path.endsWith('/csrf') ? json(csrf) : json({ error: 'Invalid request', fields: { iban: 'Bitte prüfe dieses Feld.' } }, 400))
  await assert.rejects(api.updateSettings(A, validation.toInput(form())), error => error.status === 400 && error.fields.iban === 'Bitte prüfe dieses Feld.')
})

// ------------------------------------------------------------------ validation

test('settings are normalised like on the server', () => {
  const input = validation.toInput(form({ name: '  Studio ', iban: 'ch93 0076 2011 6238 5295 7', vatNumber: 'che116281710mwst', brandColor: '#1f3a5f', street: '   ', bankName: '' }))
  assert.equal(input.name, 'Studio')
  assert.equal(input.iban, 'CH9300762011623852957')
  assert.equal(input.vatNumber, 'CHE-116.281.710 MWST')
  assert.equal(input.brandColor, '#1F3A5F')
  assert.equal(input.street, null)
  assert.equal(input.bankName, null)
  assert.equal(validation.toForm(settings).iban, 'CH93 0076 2011 6238 5295 7')
  assert.equal(validation.formatVat('de 123456789'), 'DE 123456789')
})

test('IBAN, UID, contact and colour rules match the backend', () => {
  assert.deepEqual(validation.validateSettings(form()), {})
  assert.ok(validation.ibanValid('DE89 3704 0044 0532 0130 00'))
  assert.ok(!validation.ibanValid('CH93 0076 2011 6238 5295 8'))   // checksum
  assert.ok(!validation.ibanValid('CH93 0076 2011 6238 5295'))     // Swiss length
  assert.ok(validation.swissUidValid('116281710'))
  assert.ok(!validation.swissUidValid('116281711'))
  const errors = validation.validateSettings(form({
    name: ' ', email: 'kein-mail', phone: '12', website: 'javascript:alert(1)', vatNumber: 'CHE-116.281.711',
    iban: 'CH00 1234', postalCode: '<8002>', documentFooter: 'x'.repeat(161), brandColor: '#F5F5F5',
  }))
  for (const field of ['name', 'email', 'phone', 'website', 'vatNumber', 'iban', 'postalCode', 'documentFooter', 'brandColor']) assert.ok(errors[field], field)
  assert.match(errors.brandColor, /zu hell/)
  assert.match(validation.validateSettings(form({ brandColor: 'blau' })).brandColor, /#1F3A5F/)
  assert.ok(validation.contrastOnWhite('#245747') >= 4.5)
  assert.deepEqual(validation.validateSettings(form({ vatNumber: 'ATU12345678', website: 'https://example.com/team', phone: '+49 (30) 123-456' })), {})
})

test('logos are checked for type and size before uploading', () => {
  assert.equal(validation.logoProblem({ type: 'image/png', size: 10_000 }), null)
  assert.equal(validation.logoProblem({ type: 'image/jpeg', size: validation.maxLogoBytes }), null)
  assert.match(validation.logoProblem({ type: 'image/svg+xml', size: 100 }), /PNG- oder JPEG/)
  assert.match(validation.logoProblem({ type: 'image/png', size: validation.maxLogoBytes + 1 }), /2 MB/)
  assert.match(validation.logoProblem({ type: 'image/png', size: 0 }), /leer/)
  assert.equal(validation.formatBytes(24_000), '23 KB')
})

test('server field errors map to specific client messages', () => {
  const errors = validation.serverErrors({ iban: 'Bitte prüfe dieses Feld.', street: 'Bitte prüfe dieses Feld.', unknown: 'x' }, form({ iban: 'CH00' }))
  assert.match(errors.iban, /IBAN ist ungültig/)
  assert.equal(errors.street, 'Bitte prüfe dieses Feld.')
  assert.equal(errors.unknown, undefined)
})

// ------------------------------------------------------------------ store

function deferred() {
  let resolve, reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}

test('settings store loads, saves and ignores responses after clear', async () => {
  const pending = deferred()
  const calls = []
  const store = settingsStore(A, {
    getSettings: async id => { calls.push(['get', id]); return pending.promise },
    updateSettings: async (id, input) => { calls.push(['put', id]); return { ...settings, name: input.name } },
    uploadLogo: async () => settings, removeLogo: async () => ({ ...settings, logo: null }),
  })
  const load = store.load()
  assert.equal(store.getSnapshot().status, 'loading')
  pending.resolve(settings)
  await load
  assert.equal(store.getSnapshot().settings.name, 'Studio Nord GmbH')
  assert.equal((await store.save(validation.toInput(form({ name: 'Neu' })))).name, 'Neu')
  assert.equal(store.getSnapshot().settings.name, 'Neu')
  assert.equal((await store.removeLogo()).logo, null)
  assert.deepEqual(calls, [['get', A], ['put', A]])

  // Business switch / logout: a late response is dropped and nothing remains.
  const late = deferred()
  const switching = settingsStore(A, { getSettings: () => late.promise, updateSettings: () => late.promise, uploadLogo: () => late.promise, removeLogo: () => late.promise })
  const reading = switching.load()
  const saving = switching.save(validation.toInput(form()))
  switching.clear()
  late.resolve(settings)
  await reading
  assert.equal(await saving, null)
  assert.deepEqual(switching.getSnapshot(), { status: 'loading', settings: null })
})

test('settings store rejects responses for another business and reports load errors', async () => {
  const foreign = settingsStore(A, { getSettings: async () => ({ ...settings, id: B }), updateSettings: async () => ({ ...settings, id: B }), uploadLogo: async () => settings, removeLogo: async () => settings })
  await foreign.load()
  assert.deepEqual(foreign.getSnapshot(), { status: 'error', settings: null })
  assert.equal(await foreign.save(validation.toInput(form())), null)
  const failing = settingsStore(A, { getSettings: async () => { throw new Error('offline') }, updateSettings: async () => settings, uploadLogo: async () => settings, removeLogo: async () => settings })
  await failing.load()
  assert.equal(failing.getSnapshot().status, 'error')
})

test('business store reflects a renamed business without reloading', async () => {
  const business = { id: A, name: 'Alt', industry: null, legalForm: null, country: 'CH', currency: 'CHF', createdAt: '2026-11-18T10:00:00Z', updatedAt: '2026-11-18T10:00:00Z' }
  const other = { ...business, id: B, name: 'Andere' }
  const store = createBusinessStore({ listBusinesses: async () => [business, other], createBusiness: async () => business })
  await store.refresh()
  store.replace({ ...business, name: 'Neu' })
  assert.equal(store.getSnapshot().active.name, 'Neu')
  assert.deepEqual(store.getSnapshot().businesses.map(item => item.name), ['Neu', 'Andere'])
  store.replace({ ...business, id: '44444444-4444-4444-8444-444444444444', name: 'Fremd' })
  assert.equal(store.getSnapshot().businesses.length, 2)
  assert.equal(store.getSnapshot().status, 'ready')
})
