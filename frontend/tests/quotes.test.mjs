import test from 'node:test'
import assert from 'node:assert/strict'
import { renderModule } from './helpers/render-module.mjs'

const api = await import(await renderModule('api/quotes.ts'))
const client = await import(await renderModule('api/client.ts'))
const money = await import(await renderModule('quotes/money.ts'))
const validation = await import(await renderModule('quotes/validation.ts'))
const { quoteListStore, quoteDetailStore, quoteEditorStore } = await import(await renderModule('quotes/store.ts'))
const labels = await import(await renderModule('quotes/labels.ts'))

const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const csrf = { headerName: 'X-CSRF-TOKEN', token: 'fresh-token' }
const item = { id: 'item-1', serviceId: 'service-a', position: 1, description: 'Beratung', quantity: '1.5', unit: 'HOUR', unitPrice: '95.10', taxRate: '8.10', lineSubtotal: '142.65', lineTax: '11.55', lineTotal: '154.20' }
const quote = {
  id: 'quote-a', businessId: 'business-a', customerId: 'customer-a', quoteNumber: 'ANG-0001', status: 'DRAFT',
  issueDate: '2026-11-18', validUntil: '2026-12-18', currency: 'CHF', introductionText: 'Hallo', closingText: null, notes: 'Intern',
  subtotal: '142.65', taxTotal: '11.55', grandTotal: '154.20',
  customer: { customerNumber: 'K-0001', name: 'Muster', companyName: 'Muster AG', contactName: null, street: null, postalCode: null, city: null, country: 'CH', email: null },
  items: [item], taxGroups: [{ rate: '8.10', base: '142.65', tax: '11.55' }], sentAt: null, decidedAt: null,
  createdAt: '2026-11-18T10:00:00Z', updatedAt: '2026-11-18T10:00:00Z',
}
const summary = { id: 'quote-a', businessId: 'business-a', customerId: 'customer-a', quoteNumber: 'ANG-0001', status: 'DRAFT', customerName: 'Muster', issueDate: '2026-11-18', validUntil: '2026-12-18', currency: 'CHF', grandTotal: '154.20', updatedAt: '2026-11-18T10:00:00Z' }
const input = { customerId: 'customer-a', issueDate: '2026-11-18', validUntil: '2026-12-18', currency: 'CHF', introductionText: 'Hallo', closingText: null, notes: 'Intern',
  items: [{ serviceId: 'service-a', description: 'Beratung', quantity: '1.5', unit: 'HOUR', unitPrice: '95.10', taxRate: '8.10' }] }

// ------------------------------------------------------------------ API

test('quote API scopes reads to the active business and encodes search and status', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => { calls.push({ path, options }); return json(path.includes('?') ? [summary] : quote) })
  assert.deepEqual(await api.listQuotes('business-a', { search: 'A & B', status: 'SENT' }), [summary])
  assert.deepEqual(await api.listQuotes('business-a'), [summary])
  assert.equal((await api.getQuote('business-a', 'quote-a')).grandTotal, '154.20')
  assert.equal(calls[0].path, '/api/businesses/business-a/quotes?search=A+%26+B&status=SENT')
  assert.equal(calls[1].path, '/api/businesses/business-a/quotes?search=')
  assert.equal(calls[2].path, '/api/businesses/business-a/quotes/quote-a')
  assert.ok(calls.every(call => call.options.credentials === 'same-origin'))
})

test('writes use fresh CSRF tokens and never send totals, numbers, status or IDs from loaded data', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options })
    if (path.endsWith('/csrf')) return json(csrf)
    return options.method === 'DELETE' ? new Response(null, { status: 204 }) : json(path.endsWith('/send') ? { ...quote, status: 'SENT', sentAt: '2026-11-18T11:00:00Z' } : quote)
  })
  const tampered = { ...input, grandTotal: '1.00', status: 'ACCEPTED', quoteNumber: 'X', businessId: 'foreign', items: [{ ...input.items[0], lineTotal: '0.01', id: 'x' }] }
  await api.saveQuote('business-a', tampered)
  await api.saveQuote('business-a', tampered, 'quote-a')
  assert.equal((await api.changeQuoteStatus('business-a', 'quote-a', 'send')).status, 'SENT')
  await api.deleteQuote('business-a', 'quote-a')
  const writes = calls.filter(call => call.options.method)
  assert.deepEqual(writes.map(call => [call.options.method, call.path]), [
    ['POST', '/api/businesses/business-a/quotes'], ['PUT', '/api/businesses/business-a/quotes/quote-a'],
    ['PATCH', '/api/businesses/business-a/quotes/quote-a/send'], ['DELETE', '/api/businesses/business-a/quotes/quote-a']])
  assert.deepEqual(JSON.parse(writes[0].options.body), input)
  assert.deepEqual(JSON.parse(writes[1].options.body), input)
  for (const call of writes) assert.equal(call.options.headers.get('X-CSRF-TOKEN'), 'fresh-token')
})

test('responses from another business, other IDs or with lossy money are rejected', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => json(quote))
  for (const change of [{ businessId: 'business-b' }, { id: 'quote-b' }, { grandTotal: 154.2 }, { subtotal: '1.5' }, { status: 'OPEN' },
    { quoteNumber: '42' }, { issueDate: '18.11.2026' }, { items: [{ ...item, lineTotal: 154.2 }] }, { items: [{ ...item, quantity: '1.0001' }] },
    { customer: { ...quote.customer, name: '' } }, { taxGroups: [{ rate: '8.1', base: '1.00', tax: '0.08' }] }, { items: null }]) {
    mock.mock.mockImplementation(async () => json({ ...quote, ...change }))
    await assert.rejects(api.getQuote('business-a', 'quote-a'), undefined, JSON.stringify(change))
  }
  mock.mock.mockImplementation(async () => json({ ...quote, grandTotal: '99999999999999999.99' }))
  assert.equal((await api.getQuote('business-a', 'quote-a')).grandTotal, '99999999999999999.99')
  mock.mock.mockImplementation(async () => json([{ ...summary, businessId: 'business-b' }]))
  await assert.rejects(api.listQuotes('business-a'))
  mock.mock.mockImplementation(async () => json({ rows: [] }))
  await assert.rejects(api.listQuotes('business-a'))
})

test('PDF download is an authenticated GET returning a PDF blob and reports errors', async t => {
  const calls = []
  let expired = 0
  t.after(client.subscribeUnauthorized(() => expired++))
  const mock = t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options })
    return new Response(new Uint8Array([37, 80, 68, 70]), { status: 200, headers: { 'Content-Type': 'application/pdf' } })
  })
  const blob = await api.downloadQuotePdf('business-a', 'quote-a')
  assert.equal(await blob.text(), '%PDF')
  assert.equal(calls[0].path, '/api/businesses/business-a/quotes/quote-a/pdf')
  assert.equal(calls[0].options.credentials, 'same-origin')
  assert.match(calls[0].options.headers.get('Accept'), /^application\/pdf/)
  assert.equal(calls[0].options.method, undefined)
  mock.mock.mockImplementation(async () => new Response('<html>', { status: 200, headers: { 'Content-Type': 'text/html' } }))
  await assert.rejects(api.downloadQuotePdf('business-a', 'quote-a'), /Unexpected response/)
  mock.mock.mockImplementation(async () => json({ error: 'Quote not found', fields: {} }, 404))
  await assert.rejects(api.downloadQuotePdf('business-a', 'quote-a'), error => error.status === 404)
  mock.mock.mockImplementation(async () => json({ error: 'Authentication required' }, 401))
  await assert.rejects(api.downloadQuotePdf('business-a', 'quote-a'), error => error.status === 401)
  assert.equal(expired, 1)
  const { pdfFilename } = await import(await renderModule('quotes/download.ts'))
  assert.equal(pdfFilename('ANG-0001'), 'Angebot-ANG-0001.pdf')
  assert.equal(pdfFilename('ANG-0001/../x'), 'Angebot-ANG-0001x.pdf')
})

test('server validation errors keep their field keys and mutations are not replayed', async t => {
  let writes = 0
  t.mock.method(globalThis, 'fetch', async path => {
    if (path.endsWith('/csrf')) return json(csrf)
    writes++
    return json({ error: 'Invalid request', fields: { 'items[0].serviceId': 'Bitte prüfe dieses Feld.', customerId: 'Bitte prüfe dieses Feld.' } }, 400)
  })
  await assert.rejects(api.saveQuote('business-a', input), error => error.status === 400 && Boolean(error.fields['items[0].serviceId'] && error.fields.customerId))
  assert.equal(writes, 1)
})

// ------------------------------------------------------------------ calculations

test('preview arithmetic matches the server: HALF_UP per line, exact BigInt amounts', () => {
  const cases = [
    ['1.5', '95.10', '8.10', 'CHF', '142.65', '11.55', '154.20'],
    ['0.333', '10.05', '7.70', 'CHF', '3.35', '0.26', '3.61'],
    ['0.5', '0.01', '0', 'CHF', '0.01', '0.00', '0.01'],
    ['1', '0.05', '8.10', 'CHF', '0.05', '0.00', '0.05'],
    ['1', '0.07', '8.10', 'CHF', '0.07', '0.01', '0.08'],
    ['3', '33.33', '100', 'EUR', '99.99', '99.99', '199.98'],
    ['1.5', '333', '10', 'JPY', '500.00', '50.00', '550.00'],
    ['999999999.999', '99999999999999999.99', '0', 'CHF', '99999999999899999990000000.00', '0.00', '99999999999899999990000000.00'],
  ]
  for (const [quantity, price, rate, currency, subtotal, tax, total] of cases) {
    assert.deepEqual(money.lineAmounts(quantity, price, rate, currency), { subtotal, tax, total }, `${quantity} × ${price}`)
  }
  for (const invalid of [['1e2', '1', '0'], ['1', '-1', '0'], ['1', '1.001', '0'], ['1.0001', '1', '0'], ['', '1', '0']]) {
    assert.equal(money.lineAmounts(...invalid, 'CHF'), null)
  }
  const preview = money.previewTotals([{ quantity: '0.333', unitPrice: '1.00', taxRate: '0' }, { quantity: '0.333', unitPrice: '1.00', taxRate: '0' },
    { quantity: '0.333', unitPrice: '1.00', taxRate: '0' }, { quantity: '1', unitPrice: '100', taxRate: '8.1' }, { quantity: 'x', unitPrice: '1', taxRate: '0' }], 'CHF')
  assert.equal(preview.subtotal, '100.99') // three rounded 0.33 lines + 100.00
  assert.equal(preview.taxTotal, '8.10')
  assert.equal(preview.grandTotal, '109.09')
  assert.equal(preview.complete, false)
  assert.deepEqual(preview.taxGroups, [{ rate: '0.00', base: '0.99', tax: '0.00' }, { rate: '8.10', base: '100.00', tax: '8.10' }])
  assert.equal(money.formatMoney('1234567.50', 'CHF'), 'CHF 1’234’567.50')
  assert.equal(money.formatMoney('500.00', 'JPY'), 'JPY 500')
  assert.equal(money.formatRate('8.10'), '8.1 %')
  assert.equal(money.formatRate('0.00'), '0 %')
  assert.equal(money.formatQuantity('1500.250'), '1’500.25')
  assert.equal(money.formatDate('2026-11-18'), '18.11.2026')
})

// ------------------------------------------------------------------ validation

test('draft normalization and validation mirror the server rules and field keys', () => {
  const draft = { customerId: 'customer-a', issueDate: '2026-11-18', validUntil: '2026-12-18', currency: ' chf ', introductionText: ' Hallo ', closingText: '  ', notes: '',
    items: [{ key: 'a', serviceId: null, description: ' Lizenz ', quantity: ' 2,5 ', unit: 'PIECE', unitPrice: '1234,50', taxRate: '8,1' }] }
  const normalized = validation.normalizeDraft(draft)
  assert.deepEqual(normalized, { customerId: 'customer-a', issueDate: '2026-11-18', validUntil: '2026-12-18', currency: 'CHF', introductionText: 'Hallo', closingText: null, notes: null,
    items: [{ serviceId: null, description: 'Lizenz', quantity: '2.5', unit: 'PIECE', unitPrice: '1234.50', taxRate: '8.1' }] })
  assert.deepEqual(validation.validateQuote(normalized), {})
  const valid = normalized.items[0]
  const invalid = (change, items) => validation.validateQuote({ ...normalized, ...change, items: items ?? normalized.items })
  assert.ok(invalid({ customerId: '' }).customerId)
  assert.ok(invalid({ validUntil: '2026-11-17' }).validUntil)
  assert.ok(invalid({ issueDate: '' }).issueDate)
  assert.ok(invalid({ currency: 'KWD' }).currency)
  assert.ok(invalid({ notes: 'x'.repeat(4001) }).notes)
  for (const [key, value] of [['description', ''], ['quantity', '0'], ['quantity', '1.0001'], ['quantity', '-1'], ['quantity', '1000000000'], ['unitPrice', '1.001'], ['unitPrice', ''], ['taxRate', '100.01'], ['taxRate', '']]) {
    assert.ok(invalid({}, [valid, { ...valid, [key]: value }])[`items[1].${key}`], `${key}=${value}`)
  }
  assert.ok(invalid({}, [{ ...valid, quantity: '999999999.999', unitPrice: '99999999999999999.99' }])['items[0].unitPrice'])
  assert.ok(invalid({}, Array.from({ length: 201 }, () => valid)).items)
  assert.deepEqual(invalid({ currency: 'JPY' }, [{ ...valid, unitPrice: '10.00' }]), {})
  assert.ok(invalid({ currency: 'JPY' }, [{ ...valid, unitPrice: '10.50' }])['items[0].unitPrice'])
  assert.deepEqual(invalid({}, []), {}) // empty drafts may be saved; sending is blocked separately
  assert.ok(validation.sendBlocker({ items: [] }))
  assert.equal(validation.sendBlocker({ items: [item] }), null)
})

test('catalogue positions are snapshots; positions can be added, reordered and removed', () => {
  const service = { id: 'service-a', businessId: 'business-a', name: 'Beratung', description: 'Workshop', category: null, unit: 'DAY', unitPrice: '800.00', currency: 'CHF', taxRate: null, active: true }
  const snapshot = validation.itemFromService(service)
  assert.equal(snapshot.serviceId, 'service-a')
  assert.equal(snapshot.description, 'Beratung\nWorkshop')
  assert.equal(snapshot.unit, 'DAY'); assert.equal(snapshot.unitPrice, '800.00'); assert.equal(snapshot.taxRate, '0.00'); assert.equal(snapshot.quantity, '1')
  service.unitPrice = '999.00'
  assert.equal(snapshot.unitPrice, '800.00')
  const free = validation.freeItem({ ...snapshot, taxRate: '8.10' })
  assert.equal(free.serviceId, null); assert.equal(free.taxRate, '8.10'); assert.notEqual(free.key, snapshot.key)
  assert.deepEqual(validation.moveItem(['a', 'b', 'c'], 0, 1), ['b', 'a', 'c'])
  assert.deepEqual(validation.moveItem(['a', 'b', 'c'], 2, -1), ['a', 'c', 'b'])
  assert.deepEqual(validation.moveItem(['a', 'b'], 0, -1), ['a', 'b'])
  const draft = validation.draftFromQuote(quote)
  assert.equal(draft.items[0].unitPrice, '95.10'); assert.equal(draft.closingText, ''); assert.equal(draft.notes, 'Intern')
  assert.deepEqual(validation.emptyDraft('2026-11-18', 'EUR'), { customerId: '', issueDate: '2026-11-18', validUntil: '2026-12-18', currency: 'EUR', introductionText: '', closingText: '', notes: '', items: [] })
  assert.equal(validation.addDays('2026-12-20', 30), '2027-01-19')
  assert.deepEqual(labels.quoteActions.DRAFT, ['send'])
  assert.deepEqual(labels.quoteActions.SENT, ['accept', 'reject'])
  assert.deepEqual(labels.quoteActions.ACCEPTED, [])
  assert.equal(labels.isExpired('SENT', '2026-11-17', '2026-11-18'), true)
  assert.equal(labels.isExpired('ACCEPTED', '2026-11-17', '2026-11-18'), false)
})

// ------------------------------------------------------------------ stores

test('list store distinguishes loading, empty and error; the latest filter wins', async () => {
  let fail = false
  const store = quoteListStore('business-a', { listQuotes: async () => { if (fail) throw new Error('down'); return [summary] } })
  assert.equal(store.getSnapshot().status, 'loading')
  await store.load()
  assert.deepEqual(store.getSnapshot().rows, [summary])
  fail = true; await store.load()
  assert.equal(store.getSnapshot().status, 'error'); assert.deepEqual(store.getSnapshot().rows, [])
  const pending = []
  const delayed = quoteListStore('business-a', { listQuotes: () => new Promise(resolve => pending.push(resolve)) })
  const first = delayed.load(); const second = delayed.load({ search: 'neu', status: 'SENT' })
  pending[1]([]); await second; pending[0]([summary]); await first
  assert.deepEqual(delayed.getSnapshot().rows, []); assert.equal(delayed.getSnapshot().filters.status, 'SENT')
})

test('business switch and logout clear quote data and ignore late responses', async () => {
  let resolveList
  const list = quoteListStore('business-a', { listQuotes: () => new Promise(resolve => { resolveList = resolve }) })
  const listing = list.load(); list.clear(); resolveList([summary]); await listing
  assert.deepEqual(list.getSnapshot().rows, []); assert.equal(list.getSnapshot().status, 'loading')

  let resolveQuote
  const detail = quoteDetailStore('business-a', 'quote-a', { getQuote: () => new Promise(resolve => { resolveQuote = resolve }) })
  const loading = detail.load(); detail.clear(); resolveQuote(quote); await loading
  assert.equal(detail.getSnapshot().quote, null)
  let resolveMutation
  const mutation = detail.apply(() => new Promise(resolve => { resolveMutation = resolve }))
  detail.clear(); resolveMutation({ ...quote, status: 'SENT' })
  assert.equal(await mutation, false)
  assert.equal(detail.getSnapshot().quote, null)

  const pending = []
  const editor = quoteEditorStore('business-a', 'quote-a', {
    getQuote: () => new Promise(resolve => pending.push(resolve)), listCustomers: async () => [{ id: 'c' }], listServices: async () => [{ id: 's' }] })
  const editing = editor.load(); editor.clear(); pending[0](quote); await editing
  assert.deepEqual(editor.getSnapshot(), { status: 'loading', quote: null, customers: [], services: [] })
  let resolveSave
  const saving = editor.save(() => new Promise(resolve => { resolveSave = resolve }))
  editor.clear(); resolveSave(quote)
  assert.equal(await saving, null)
})

test('detail and editor stores load scoped data, apply confirmed mutations and surface errors', async () => {
  const requests = []
  const detail = quoteDetailStore('business-a', 'quote-a', { getQuote: async (business, id) => { requests.push([business, id]); return quote } })
  await detail.load()
  assert.deepEqual(requests, [['business-a', 'quote-a']]); assert.equal(detail.getSnapshot().status, 'ready')
  assert.equal(await detail.apply(async () => ({ ...quote, status: 'ACCEPTED' })), true)
  assert.equal(detail.getSnapshot().quote.status, 'ACCEPTED')
  await assert.rejects(detail.apply(async () => { throw new Error('409') }))
  assert.equal(detail.getSnapshot().quote.status, 'ACCEPTED')
  const missing = quoteDetailStore('business-a', 'quote-x', { getQuote: async () => { throw new Error('404') } })
  await missing.load(); assert.equal(missing.getSnapshot().status, 'error')

  const calls = []
  const editor = quoteEditorStore('business-a', undefined, {
    getQuote: async () => { throw new Error('not used') },
    listCustomers: async business => { calls.push(['customers', business]); return [{ id: 'c' }] },
    listServices: async (business, filters) => { calls.push(['services', business, filters.active]); return [{ id: 's' }] } })
  await editor.load()
  assert.deepEqual(editor.getSnapshot(), { status: 'ready', quote: null, customers: [{ id: 'c' }], services: [{ id: 's' }] })
  assert.deepEqual(calls, [['customers', 'business-a'], ['services', 'business-a', true]])
  const failing = quoteEditorStore('business-a', 'quote-a', { getQuote: async () => quote, listCustomers: async () => { throw new Error('down') }, listServices: async () => [] })
  await failing.load(); assert.equal(failing.getSnapshot().status, 'error')
})
