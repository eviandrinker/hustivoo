import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

async function moduleUrl(file, replacements = {}) {
  let source = await readFile(new URL('../src/' + file, import.meta.url), 'utf8')
  for (const [from, to] of Object.entries(replacements)) source = source.replace(from, to)
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2023, module: ts.ModuleKind.ESNext } }).outputText
  return `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`
}
const clientUrl = await moduleUrl('api/client.ts')
const api = await import(await moduleUrl('api/business.ts', { "'./client'": JSON.stringify(clientUrl) }))
const { createBusinessStore, businessDestination } = await import(await moduleUrl('business/store.ts'))
const { validateBusiness } = await import(await moduleUrl('business/validation.ts'))
const one = { id: '12345678-1234-1234-1234-123456789012', name: 'Test company', industry: null, legalForm: null, country: 'CH', currency: 'CHF', createdAt: '2026-11-18T10:00:00Z', updatedAt: '2026-11-18T10:00:00Z' }
const two = { ...one, id: '22345678-1234-1234-1234-123456789012', name: 'Second company' }
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

test('business client lists, retrieves and creates with existing session and CSRF handling', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options })
    if (path.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })
    if (options.method === 'POST') return json(one, 201)
    return json(path === '/api/businesses' ? [one] : one)
  })
  assert.deepEqual(await api.listBusinesses(), [one])
  assert.deepEqual(await api.getBusiness(one.id), one)
  assert.deepEqual(await api.createBusiness({ name: 'Test company', country: 'CH', currency: 'CHF' }), one)
  const post = calls.find(call => call.options.method === 'POST')
  assert.equal(post.options.credentials, 'same-origin')
  assert.equal(post.options.headers.get('X-CSRF-TOKEN'), 'csrf')
})

test('business API rejects malformed responses, HTTP failures and network failures', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => json({ businesses: [] }))
  await assert.rejects(api.listBusinesses())
  mock.mock.mockImplementation(async () => json([{ ...one, currency: null }]))
  await assert.rejects(api.listBusinesses())
  mock.mock.mockImplementation(async () => json({}, 500))
  await assert.rejects(api.listBusinesses(), error => error.status === 500)
  mock.mock.mockImplementation(async () => { throw new TypeError('offline') })
  await assert.rejects(api.listBusinesses())
})

test('no premature redirect, empty membership list onboards', async () => {
  const store = createBusinessStore({ listBusinesses: async () => [], createBusiness: async () => one })
  assert.equal(businessDestination(store.getSnapshot()), 'loading')
  await store.refresh()
  assert.equal(businessDestination(store.getSnapshot()), '/onboarding')
})

test('restored session loads existing companies and switches only among memberships', async () => {
  const store = createBusinessStore({ listBusinesses: async () => [one, two], createBusiness: async () => one })
  await store.refresh()
  assert.equal(businessDestination(store.getSnapshot()), '/dashboard')
  assert.equal(store.getSnapshot().active.id, one.id)
  store.select(two.id)
  await store.refresh()
  assert.equal(store.getSnapshot().active.id, two.id)
  assert.throws(() => store.select('foreign-id'))
})

test('successful creation selects the server response and unlocks dashboard', async () => {
  const store = createBusinessStore({ listBusinesses: async () => [], createBusiness: async () => one })
  await store.refresh()
  await store.create({ name: one.name, country: 'CH', currency: 'CHF' })
  assert.equal(businessDestination(store.getSnapshot()), '/dashboard')
  assert.deepEqual(store.getSnapshot().active, one)
})

test('validation follows name and optional text limits and ISO code shapes', () => {
  assert.deepEqual(validateBusiness({ name: 'Studio', country: 'CH', currency: 'CHF' }), {})
  const invalid = validateBusiness({ name: ' ', industry: 'x'.repeat(121), legalForm: 'x'.repeat(121), country: 'Switzerland', currency: '' })
  assert.equal(Object.keys(invalid).length, 5)
})

test('API failure is not an empty business list; failed creation does not fabricate a company', async () => {
  const store = createBusinessStore({ listBusinesses: async () => { throw new Error('offline') }, createBusiness: async () => { throw new Error('invalid') } })
  await store.refresh()
  assert.equal(businessDestination(store.getSnapshot()), 'error')
  await assert.rejects(store.create({ name: 'Studio', country: 'CH', currency: 'CHF' }))
  assert.equal(store.getSnapshot().active, null)
})

test('logout clears state and late responses cannot restore previous account data', async () => {
  let resolve
  const store = createBusinessStore({ listBusinesses: () => new Promise(done => { resolve = done }), createBusiness: async () => one })
  const pending = store.refresh()
  store.clear()
  resolve([one])
  await pending
  assert.deepEqual(store.getSnapshot().businesses, [])
  assert.equal(store.getSnapshot().active, null)
  const creation = store.create({ name: 'Studio', country: 'CH', currency: 'CHF' })
  store.clear()
  await assert.rejects(creation)
  assert.equal(store.getSnapshot().active, null)
})

const options = await import(await moduleUrl('business/onboarding-options.ts'))
test('onboarding choices preserve API values and optional custom text', () => {
  assert.equal(options.optionalSelection('', ''), null)
  assert.equal(options.optionalSelection('GmbH', ''), 'GmbH')
  assert.equal(options.optionalSelection('other', '  Musikunterricht  '), 'Musikunterricht')
  assert.equal(options.optionalSelection('other', ''), 'Sonstiges')
  assert.equal(options.countries[0][0], 'CH')
  assert.equal(options.currencies[0][0], 'CHF')
  for (const [code] of options.countries) assert.match(code, /^[A-Z]{2}$/)
  for (const [code] of options.currencies) assert.match(code, /^[A-Z]{3}$/)
})
test('empty business name has actionable field-specific validation', () => {
  assert.equal(validateBusiness({ name: ' ', country: 'CH', currency: 'CHF' }).name, 'Bitte gib einen Unternehmensnamen ein.')
})
