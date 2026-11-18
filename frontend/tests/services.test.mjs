import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
async function moduleUrl(file, replacements = {}) {
  let source = await readFile(new URL('../src/' + file, import.meta.url), 'utf8')
  for (const [from, to] of Object.entries(replacements)) source = source.replaceAll(from, to)
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2023, module: ts.ModuleKind.ESNext } }).outputText
  return `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`
}
const clientUrl = await moduleUrl('api/client.ts')
const apiUrl = await moduleUrl('api/services.ts', { "'./client'": JSON.stringify(clientUrl) })
const api = await import(apiUrl)
const { serviceStore } = await import(await moduleUrl('services/store.ts', { "'../api/services'": JSON.stringify(apiUrl) }))
const { normalizeService, validateService, formatPrice } = await import(await moduleUrl('services/validation.ts'))
const input = { name: 'Beratung', description: 'Strategie', category: 'Consulting', unit: 'HOUR', unitPrice: '95.10', currency: 'CHF', taxRate: '8.10' }
const row = { ...input, id: 'service-a', businessId: 'business-a', active: true, createdAt: '2026-11-18T12:00:00Z', updatedAt: '2026-11-18T12:00:00Z' }
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const defaults = api.defaultServiceFilters

test('service API scopes reads, encodes filters, and preserves decimal strings', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options })
    return json(path.includes('?') ? [row] : row)
  })
  const filters = { search: 'A & B', category: 'Consulting', active: false, sort: 'PRICE_DESC' }
  assert.deepEqual(await api.listServices('business-a', filters), [row])
  assert.deepEqual(await api.getService('business-a', row.id), row)
  assert.match(calls[0].path, /search=A\+%26\+B&category=Consulting&active=false&sort=PRICE_DESC/)
  assert.equal(calls[1].path, '/api/businesses/business-a/services/service-a')
  assert.equal(calls[0].options.credentials, 'same-origin')
})
test('create, edit, archive and restore use CSRF and whitelist writable fields', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options })
    return json(path.endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'fresh-token' } : row)
  })
  await api.saveService('business-a', row)
  await api.saveService('business-a', row, row.id)
  await api.archiveService('business-a', row.id, true)
  await api.archiveService('business-a', row.id, false)
  const writes = calls.filter(call => call.options.method)
  assert.deepEqual(writes.map(call => call.options.method), ['POST', 'PUT', 'PATCH', 'PATCH'])
  assert.deepEqual(JSON.parse(writes[0].options.body), input)
  assert.equal(calls.filter(call => call.path.endsWith('/csrf')).length, 4)
  for (const call of writes) { assert.equal(call.options.headers.get('X-CSRF-TOKEN'), 'fresh-token'); assert.equal(call.options.credentials, 'same-origin') }
  assert.ok(writes[2].path.endsWith('/archive')); assert.ok(writes[3].path.endsWith('/restore'))
})
test('response validation rejects foreign scope, mismatched IDs and lossy or malformed money', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => json(row))
  for (const change of [{ businessId: 'foreign' }, { id: 'foreign' }, { unitPrice: 95.10 }, { unitPrice: '-1.00' }, { taxRate: '101.00' }, { unit: 'YEAR' }, { active: null }, { createdAt: 'invalid' }]) {
    mock.mock.mockImplementation(async () => json({ ...row, ...change }))
    await assert.rejects(api.getService('business-a', row.id))
  }
  mock.mock.mockImplementation(async () => json({ ...row, unitPrice: '99999999999999999.99' }))
  assert.equal((await api.getService('business-a', row.id)).unitPrice, '99999999999999999.99')
  mock.mock.mockImplementation(async () => json({ rows: [] }))
  await assert.rejects(api.listServices('business-a'))
})
test('API propagates server validation and network errors without replaying mutations', async t => {
  let writes = 0
  const mock = t.mock.method(globalThis, 'fetch', async path => {
    if (path.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'token' })
    writes++
    return json({ fields: { unitPrice: 'Bitte prüfen' } }, 400)
  })
  await assert.rejects(api.saveService('business-a', input), error => error.status === 400 && error.fields.unitPrice === 'Bitte prüfen')
  assert.equal(writes, 1)
  mock.mock.mockImplementation(async () => { throw new TypeError('Network') })
  await assert.rejects(api.listServices('business-a'))
  mock.mock.mockImplementation(async () => json({}, 500))
  await assert.rejects(api.listServices('business-a'), error => error.status === 500)
})
function fixture() {
  let rows = []
  return {
    listServices: async (business, filters) => rows.filter(row => row.businessId === business && row.active === filters.active && row.name.includes(filters.search) && (!filters.category || row.category === filters.category)),
    saveService: async (_business, data, id) => { const saved = { ...row, ...data, id: id ?? row.id }; rows = [...rows.filter(row => row.id !== saved.id), saved]; return saved },
    archiveService: async (_business, id, archived) => { rows = rows.map(row => row.id === id ? { ...row, active: !archived } : row); return rows[0] },
  }
}
test('list initializes loading, resolves empty, creates and edits then archives/restores', async () => {
  const store = serviceStore('business-a', fixture())
  assert.equal(store.getSnapshot().status, 'loading')
  await store.load()
  assert.equal(store.getSnapshot().status, 'ready'); assert.deepEqual(store.getSnapshot().rows, [])
  await store.save(input)
  assert.equal(store.getSnapshot().rows[0].name, 'Beratung')
  await store.save({ ...input, name: 'Design' }, row.id)
  assert.equal(store.getSnapshot().rows[0].name, 'Design')
  await store.archive(row.id, true)
  assert.deepEqual(store.getSnapshot().rows, [])
  await store.load({ ...defaults, active: false })
  assert.equal(store.getSnapshot().rows[0].active, false)
  await store.archive(row.id, false)
  assert.deepEqual(store.getSnapshot().rows, [])
  await store.load()
  await store.load(defaults)
  assert.equal(store.getSnapshot().rows[0].active, true)
})
test('search, category, status and sorting survive refresh after writes', async () => {
  const calls = []; const source = fixture()
  const store = serviceStore('business-a', { ...source, listServices: async (business, filters) => { calls.push(filters); return source.listServices(business, filters) } })
  const filters = { ...defaults, search: 'Ber', category: 'Consulting', sort: 'PRICE_DESC' }
  await store.load(filters); await store.save(input)
  assert.deepEqual(calls.at(-1), filters)
  assert.equal(store.getSnapshot().rows.length, 1)
  await store.load({ ...filters, search: 'No match' })
  assert.deepEqual(store.getSnapshot().rows, [])
})
test('load errors clear rows and remain distinct from empty results; retry recovers', async () => {
  let fail = false
  const store = serviceStore('business-a', { ...fixture(), listServices: async () => { if (fail) throw new Error(); return [row] } })
  await store.load(); fail = true; await store.load()
  assert.equal(store.getSnapshot().status, 'error'); assert.deepEqual(store.getSnapshot().rows, [])
  fail = false; await store.load()
  assert.equal(store.getSnapshot().status, 'ready')
})
test('latest search wins over a delayed response', async () => {
  const pending = []
  const store = serviceStore('business-a', { ...fixture(), listServices: () => new Promise(resolve => pending.push(resolve)) })
  const first = store.load(); const second = store.load({ ...defaults, search: 'new' })
  pending[1]([]); await second; pending[0]([row]); await first
  assert.deepEqual(store.getSnapshot().rows, []); assert.equal(store.getSnapshot().filters.search, 'new')
})
test('business change and logout clear rows and ignore delayed reads', async () => {
  let resolve
  const old = serviceStore('business-a', { ...fixture(), listServices: () => new Promise(done => { resolve = done }) })
  const pending = old.load(); old.clear()
  const next = serviceStore('business-b', fixture()); await next.load()
  resolve([row]); await pending
  assert.deepEqual(old.getSnapshot().rows, []); assert.deepEqual(next.getSnapshot().rows, [])
  const active = serviceStore('business-a', fixture()); await active.save(input); active.clear()
  assert.deepEqual(active.getSnapshot().rows, []); assert.deepEqual(active.getSnapshot().filters, defaults)
})
test('cleanup during save or archive prevents old-business refreshes', async () => {
  for (const operation of ['save', 'archive']) {
    let resolve; let reads = 0
    const mutation = () => new Promise(done => { resolve = done })
    const store = serviceStore('business-a', { listServices: async () => { reads++; return [row] }, saveService: mutation, archiveService: mutation })
    const pending = operation === 'save' ? store.save(input) : store.archive(row.id, true)
    store.clear(); resolve(row); await pending
    assert.equal(reads, 0); assert.deepEqual(store.getSnapshot().rows, [])
  }
})
test('failed writes propagate; confirmed writes with failed refresh are not replayed', async () => {
  let writes = 0
  const source = fixture()
  const store = serviceStore('business-a', { ...source, saveService: async () => { writes++; return row }, listServices: async () => { throw new Error() } })
  await store.save(input); assert.equal(writes, 1); assert.equal(store.getSnapshot().status, 'error')
  await store.load(); assert.equal(writes, 1)
  const failure = serviceStore('business-a', { ...source, archiveService: async () => { throw new Error('failure') } })
  await assert.rejects(failure.archive(row.id, true))
})
test('form normalizes optional values, accepts decimal commas and enforces precision and limits', () => {
  const normalized = normalizeService({ ...input, name: ' Beratung ', description: ' ', category: '', unitPrice: ' 95,10 ', currency: ' chf ', taxRate: '8,10' })
  assert.deepEqual(normalized, { ...input, name: 'Beratung', description: null, category: null })
  assert.deepEqual(validateService(normalized), {})
  assert.deepEqual(validateService({ ...input, unitPrice: '99999999999999999.99', taxRate: '100.00' }), {})
  for (const price of ['', '-1', '1.001', '1e2', '100000000000000000', 'NaN']) assert.ok(validateService({ ...input, unitPrice: price }).unitPrice)
  for (const rate of ['-1', '100.01', '1.001']) assert.ok(validateService({ ...input, taxRate: rate }).taxRate)
  assert.deepEqual(validateService({ ...input, unitPrice: '0', taxRate: null }), {})
  for (const [key, value] of Object.entries({ name: '', description: 'x'.repeat(4001), category: 'x'.repeat(121), currency: 'XX', unit: 'YEAR' })) assert.ok(validateService({ ...input, [key]: value })[key])
  assert.equal(formatPrice('99999999999999999.99', 'CHF'), 'CHF 99’999’999’999’999’999.99')
})
