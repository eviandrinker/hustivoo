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
const api = await import(await moduleUrl('api/customers.ts', { "'./client'": JSON.stringify(clientUrl) }))
const { customerStore } = await import(await moduleUrl('customers/store.ts'))
const { validateCustomer } = await import(await moduleUrl('customers/validation.ts'))
const input = { type: 'PRIVATE', displayName: 'Ada', companyName: null, firstName: 'Ada', lastName: null, email: null, phone: null, street: null, postalCode: null, city: null, country: 'CH', notes: null }
const row = { ...input, id: 'customer-a', businessId: 'business-a', customerNumber: 'K-0001', status: 'ACTIVE', archived: false, createdAt: '2026-11-18T12:00:00Z', updatedAt: '2026-11-18T12:00:00Z' }
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
test('API supports scoped search, detail, create, edit, archive and restore with CSRF', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options })
    if (path.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })
    return json(path.includes('?') ? [row] : row)
  })
  assert.deepEqual(await api.listCustomers('business-a', 'Ada & Co', true), [row])
  assert.deepEqual(await api.getCustomer('business-a', row.id), row)
  await api.saveCustomer('business-a', input)
  await api.saveCustomer('business-a', input, row.id)
  await api.archiveCustomer('business-a', row.id, true)
  await api.archiveCustomer('business-a', row.id, false)
  assert.match(calls[0].path, /search=Ada\+%26\+Co&includeArchived=true/)
  const writes = calls.filter(call => ['POST', 'PUT', 'PATCH'].includes(call.options.method))
  assert.deepEqual(writes.map(call => call.options.method), ['POST', 'PUT', 'PATCH', 'PATCH'])
  for (const call of writes) { assert.equal(call.options.headers.get('X-CSRF-TOKEN'), 'csrf'); assert.equal(call.options.credentials, 'same-origin') }
  assert.ok(writes[2].path.endsWith('/archive')); assert.ok(writes[3].path.endsWith('/restore'))
})
test('API rejects foreign-business rows, malformed data and server errors', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => json([{ ...row, businessId: 'foreign' }]))
  await assert.rejects(api.listCustomers('business-a'))
  mock.mock.mockImplementation(async () => json([{ id: 'missing' }]))
  await assert.rejects(api.listCustomers('business-a'))
  mock.mock.mockImplementation(async () => json({}, 500))
  await assert.rejects(api.listCustomers('business-a'), error => error.status === 500)
})
test('field validation errors survive the shared client safely', async t => {
  t.mock.method(globalThis, 'fetch', async path => path.endsWith('/csrf') ? json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }) : json({ error: 'Invalid request', fields: { email: 'Bitte prüfen', unsafe: {} } }, 400))
  await assert.rejects(api.saveCustomer('business-a', input), error => error.status === 400 && error.fields.email === 'Bitte prüfen' && !('unsafe' in error.fields))
})
function fixture() {
  let rows = []
  return {
    listCustomers: async (_business, search, archived) => rows.filter(row => (archived || !row.archived) && row.displayName.includes(search)),
    saveCustomer: async (_business, data, id) => { const saved = { ...row, ...data, id: id ?? row.id }; rows = [...rows.filter(row => row.id !== saved.id), saved]; return saved },
    archiveCustomer: async (_business, id, archived) => { rows = rows.map(row => row.id === id ? { ...row, archived } : row); return rows[0] },
  }
}
test('store handles empty list, creation, editing, search, archive and restore', async () => {
  const store = customerStore('business-a', fixture())
  assert.equal(store.getSnapshot().status, 'loading')
  await store.load(); assert.deepEqual(store.getSnapshot().rows, [])
  await store.save(input); assert.equal(store.getSnapshot().rows[0].displayName, 'Ada')
  await store.save({ ...input, displayName: 'Updated' }, row.id); assert.equal(store.getSnapshot().rows[0].displayName, 'Updated')
  await store.load('missing'); assert.equal(store.getSnapshot().rows.length, 0)
  await store.load('Updated'); assert.equal(store.getSnapshot().rows.length, 1)
  await store.archive(row.id, true); assert.equal(store.getSnapshot().rows.length, 0)
  await store.load('', true); assert.equal(store.getSnapshot().rows[0].archived, true)
  await store.archive(row.id, false); assert.equal(store.getSnapshot().rows[0].archived, false)
})
test('network errors remain errors rather than empty-state success', async () => {
  const store = customerStore('business-a', { ...fixture(), listCustomers: async () => { throw new Error('offline') } })
  await store.load(); assert.equal(store.getSnapshot().status, 'error')
  assert.deepEqual(store.getSnapshot().rows, [])
})
test('switch and logout cleanup discard late loads and late mutation reloads', async () => {
  let resolve
  const previous = customerStore('business-a', { ...fixture(), listCustomers: () => new Promise(done => { resolve = done }) })
  const pending = previous.load()
  previous.clear()
  const next = customerStore('business-b', fixture())
  await next.load()
  resolve([row]); await pending
  assert.deepEqual(previous.getSnapshot().rows, [])
  assert.deepEqual(next.getSnapshot().rows, [])
  let finish
  let reloads = 0
  const store = customerStore('business-a', { ...fixture(), saveCustomer: () => new Promise(done => { finish = done }), listCustomers: async () => { reloads++; return [row] } })
  const write = store.save(input)
  store.clear(); finish(row); await write
  assert.equal(reloads, 0)
  assert.deepEqual(store.getSnapshot().rows, [])
})
test('new search wins when prior responses arrive late', async () => {
  const pending = []
  const store = customerStore('business-a', { ...fixture(), listCustomers: () => new Promise(resolve => pending.push(resolve)) })
  const first = store.load('first'); const second = store.load('second')
  pending[1]([]); await second; pending[0]([row]); await first
  assert.equal(store.getSnapshot().search, 'second')
  assert.deepEqual(store.getSnapshot().rows, [])
})
test('customer validation covers required name, lengths and email', () => {
  assert.deepEqual(validateCustomer(input), {})
  const errors = validateCustomer({ ...input, displayName: '', email: 'bad', notes: 'a'.repeat(4001), country: 'Switzerland' })
  assert.deepEqual(Object.keys(errors).sort(), ['country', 'displayName', 'email', 'notes'])
})

const { customerDetailStore } = await import(await moduleUrl('customers/detail-store.ts'))
const { validateContact, validateNote } = await import(await moduleUrl('customers/validation.ts'))
const contactInput = { firstName: 'Anna', lastName: 'Muster', jobTitle: 'Inhaberin', email: 'anna@example.com', phone: null, primaryContact: true }
const contactRow = { ...contactInput, id: 'contact-a', businessId: row.businessId, customerId: row.id }
const noteRow = { id: 'note-a', businessId: row.businessId, customerId: row.id, content: 'Nächstes Gespräch am Freitag', createdBy: 'user-a', authorName: 'team@example.com', createdAt: row.createdAt }
const activityRow = { id: 'activity-a', businessId: row.businessId, customerId: row.id, event: 'CREATED', createdBy: 'user-a', authorName: 'team@example.com', createdAt: row.createdAt }
const detailApi = () => ({ getCustomer: async () => row, listContacts: async () => [contactRow], listNotes: async () => [noteRow], listActivities: async () => [activityRow] })

test('CRM API sends filters and sort, validates number and status', async t => {
  let called
  const mock = t.mock.method(globalThis, 'fetch', async path => { called = path; return json([row]) })
  await api.listCustomers('business-a', 'K-0001', false, { status: 'LEAD', type: 'COMPANY', sort: 'CREATED_DESC' })
  const params = new URL(called, 'http://localhost').searchParams
  assert.equal(params.get('status'), 'LEAD'); assert.equal(params.get('type'), 'COMPANY'); assert.equal(params.get('sort'), 'CREATED_DESC')
  mock.mock.mockImplementation(async () => json([{ ...row, customerNumber: null }]))
  await assert.rejects(api.listCustomers('business-a'))
  mock.mock.mockImplementation(async () => json([{ ...row, status: 'OTHER' }]))
  await assert.rejects(api.listCustomers('business-a'))
})

test('CRM API loads contacts, notes and activities in the customer scope', async t => {
  t.mock.method(globalThis, 'fetch', async path => json(path.endsWith('/contacts') ? [contactRow] : path.endsWith('/notes') ? [noteRow] : [activityRow]))
  assert.deepEqual(await api.listContacts(row.businessId, row.id), [contactRow])
  assert.deepEqual(await api.listNotes(row.businessId, row.id), [noteRow])
  assert.deepEqual(await api.listActivities(row.businessId, row.id), [activityRow])
})

test('CRM mutations use CSRF and do not send creator or activity fields', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options })
    if (path.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })
    if (options.method === 'DELETE') return new Response(null, { status: 204 })
    if (path.endsWith('/status')) return json({ ...row, status: 'LEAD' })
    if (path.endsWith('/notes')) return json(noteRow, 201)
    return json(contactRow)
  })
  assert.equal((await api.updateCustomerStatus(row.businessId, row.id, 'LEAD')).status, 'LEAD')
  await api.saveContact(row.businessId, row.id, contactInput)
  await api.saveContact(row.businessId, row.id, { ...contactInput, primaryContact: false }, contactRow.id)
  await api.removeContact(row.businessId, row.id, contactRow.id)
  await api.addNote(row.businessId, row.id, noteRow.content)
  const writes = calls.filter(call => call.options.method)
  assert.deepEqual(writes.map(call => call.options.method), ['PATCH', 'POST', 'PUT', 'DELETE', 'POST'])
  for (const { options } of writes) assert.equal(options.headers.get('X-CSRF-TOKEN'), 'csrf')
  assert.deepEqual(JSON.parse(writes[4].options.body), { content: noteRow.content })
})

test('CRM responses with foreign parent, invalid activity or malformed fields are rejected', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => json([{ ...contactRow, customerId: 'foreign' }]))
  await assert.rejects(api.listContacts(row.businessId, row.id))
  mock.mock.mockImplementation(async () => json([{ ...noteRow, businessId: 'foreign' }]))
  await assert.rejects(api.listNotes(row.businessId, row.id))
  mock.mock.mockImplementation(async () => json([{ ...activityRow, event: 'CLIENT_INVENTED' }]))
  await assert.rejects(api.listActivities(row.businessId, row.id))
  mock.mock.mockImplementation(async () => json([{ ...contactRow, primaryContact: 'true' }]))
  await assert.rejects(api.listContacts(row.businessId, row.id))
})

test('detail state loads all panels and refreshes after mutations', async () => {
  const source = detailApi()
  const store = customerDetailStore(row.businessId, row.id, source)
  assert.equal(store.getSnapshot().status, 'loading')
  await store.load()
  assert.equal(store.getSnapshot().status, 'ready')
  assert.deepEqual(store.getSnapshot().data.contacts, [contactRow])
  source.getCustomer = async () => ({ ...row, status: 'LEAD' })
  await store.mutate(async () => {})
  assert.equal(store.getSnapshot().data.customer.status, 'LEAD')
  await assert.rejects(store.mutate(async () => { throw new Error('offline') }))
  assert.equal(store.getSnapshot().data.customer.status, 'LEAD')
})

test('detail load failure clears data; refresh failure after confirmed mutation does not replay it', async () => {
  const source = detailApi()
  const store = customerDetailStore(row.businessId, row.id, source)
  await store.load()
  source.listNotes = async () => { throw new Error('offline') }
  let writes = 0
  assert.equal(await store.mutate(async () => { writes++ }), true)
  assert.equal(writes, 1)
  assert.equal(store.getSnapshot().status, 'error')
  assert.equal(store.getSnapshot().data, null)
})

test('business switch and logout dispose CRM details and ignore late requests', async () => {
  let finish
  const source = { ...detailApi(), getCustomer: () => new Promise(resolve => { finish = resolve }) }
  const store = customerDetailStore(row.businessId, row.id, source)
  const loading = store.load()
  store.clear()
  finish(row); await loading
  assert.equal(store.getSnapshot().data, null)
  let finishWrite
  const write = store.mutate(() => new Promise(resolve => { finishWrite = resolve }))
  store.clear(); finishWrite(); assert.equal(await write, false)
  assert.equal(store.getSnapshot().data, null)
})

test('contact and note validation enforce required names, email and content lengths', () => {
  assert.deepEqual(validateContact(contactInput), {})
  const invalid = validateContact({ ...contactInput, firstName: '', lastName: 'x'.repeat(121), email: 'invalid' })
  assert.deepEqual(Object.keys(invalid).sort(), ['email', 'firstName', 'lastName'])
  assert.equal(validateNote(''), 'Bitte schreibe eine Notiz.')
  assert.equal(validateNote('x'.repeat(4001)), 'Maximal 4000 Zeichen.')
  assert.equal(validateNote(' Eine Notiz '), undefined)
})

test('list refresh preserves status/type/sort selection after a write', async () => {
  const calls = []
  const store = customerStore(row.businessId, { ...fixture(), listCustomers: async (...args) => { calls.push(args); return [row] } })
  const filters = { status: 'ACTIVE', type: 'PRIVATE', sort: 'CREATED_DESC' }
  await store.load('Ada', false, filters)
  await store.save(input)
  assert.deepEqual(calls[1], [row.businessId, 'Ada', false, filters])
})
