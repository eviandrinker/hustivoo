import test from 'node:test'
import assert from 'node:assert/strict'
import { renderModule } from './helpers/render-module.mjs'
import { B, invoice, payment, summary } from './helpers/invoice-fixture.mjs'

const api = await import(await renderModule('api/invoices.ts'))
const validation = await import(await renderModule('invoices/validation.ts'))
const labels = await import(await renderModule('invoices/labels.ts'))
const { invoiceListStore, invoiceDetailStore, invoiceEditorStore } = await import(await renderModule('invoices/store.ts'))

const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const csrf = { headerName: 'X-CSRF-TOKEN', token: 'fresh-token' }

// ------------------------------------------------------------------ API

test('invoice API scopes requests to the business and encodes filters', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => { calls.push({ path, options }); return json(path.includes('?') ? [summary] : invoice) })
  assert.deepEqual(await api.listInvoices(B, { search: 'A & B', status: 'OVERDUE' }), [summary])
  assert.equal((await api.getInvoice(B, 'invoice-a')).quote.quoteNumber, 'ANG-0007')
  assert.equal(calls[0].path, '/api/businesses/business-a/invoices?search=A+%26+B&status=OVERDUE')
  assert.equal(calls[1].path, '/api/businesses/business-a/invoices/invoice-a')
  assert.ok(calls.every(call => call.options.credentials === 'same-origin'))
})

test('saving sends only writable fields with a CSRF token; actions use their endpoints', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => { calls.push({ path, options }); return path.endsWith('/csrf') ? json(csrf) : json(invoice) })
  const input = { customerId: 'customer-a', issueDate: '2026-11-18', paymentTermDays: 30, currency: 'CHF', introductionText: null, closingText: null, notes: null,
    items: [{ serviceId: null, description: 'A', quantity: '1', unit: 'PIECE', unitPrice: '10.00', taxRate: '0.00', lineTotal: '999.00' }], grandTotal: '1.00', status: 'PAID', paidTotal: '9' }
  await api.saveInvoice(B, input, 'invoice-a')
  await api.issueInvoice(B, 'invoice-a')
  await api.cancelInvoice(B, 'invoice-a', 'Doppelt')
  await api.recordPayment(B, 'invoice-a', { paymentDate: '2026-11-18', amount: '10.00', method: 'CASH', reference: null, note: null, invoiceId: 'other' })
  await api.reversePayment(B, 'invoice-a', 'payment-1', 'Falsch')
  const writes = calls.filter(call => !call.path.endsWith('/csrf'))
  assert.deepEqual(writes.map(call => [call.options.method, call.path.replace('/api/businesses/business-a/invoices/invoice-a', '')]), [
    ['PUT', ''], ['PATCH', '/issue'], ['PATCH', '/cancel'], ['POST', '/payments'], ['PATCH', '/payments/payment-1/reverse']])
  for (const call of writes) assert.equal(new Headers(call.options.headers).get('X-CSRF-TOKEN'), 'fresh-token')
  const body = JSON.parse(writes[0].options.body)
  assert.deepEqual(Object.keys(body).sort(), ['closingText', 'currency', 'customerId', 'introductionText', 'issueDate', 'items', 'notes', 'paymentTermDays'])
  assert.equal(body.items[0].lineTotal, undefined)
  assert.deepEqual(JSON.parse(writes[2].options.body), { reason: 'Doppelt' })
  assert.equal(JSON.parse(writes[3].options.body).invoiceId, undefined)
})

test('converting a quote twice resolves to the existing invoice', async t => {
  let first = true
  t.mock.method(globalThis, 'fetch', async path => {
    if (path.endsWith('/csrf')) return json(csrf)
    if (first) { first = false; return json({ ...invoice, status: 'DRAFT', displayStatus: 'DRAFT' }, 201) }
    return json({ error: 'Quote already invoiced', fields: {}, invoiceId: '11111111-1111-4111-8111-111111111111' }, 409)
  })
  assert.deepEqual(await api.invoiceFromQuote(B, 'quote-a'), { id: 'invoice-a', created: true })
  assert.deepEqual(await api.invoiceFromQuote(B, 'quote-a'), { id: '11111111-1111-4111-8111-111111111111', created: false })
})

test('a not accepted quote is reported as a conflict', async t => {
  t.mock.method(globalThis, 'fetch', async path => path.endsWith('/csrf') ? json(csrf) : json({ error: 'Only accepted quotes can be invoiced', fields: {}, invoiceId: null }, 409))
  await assert.rejects(api.invoiceFromQuote(B, 'quote-a'), error => error.status === 409 && error.invoiceId === undefined)
})

test('invoice responses are validated', () => {
  assert.equal(api.parseInvoice({ ...invoice, payments: [payment] }, B).payments[0].amount, '50.00')
  for (const broken of [{ ...invoice, businessId: 'other' }, { ...invoice, invoiceNumber: 'ANG-0001' }, { ...invoice, displayStatus: 'LATE' },
    { ...invoice, openAmount: 1 }, { ...invoice, qrBill: { available: 'yes', problems: [] } }, { ...invoice, payments: [{ ...payment, method: 'BITCOIN' }] }]) {
    assert.throws(() => api.parseInvoice(broken, B))
  }
})

// ------------------------------------------------------------------ validation

test('drafts derive the due date and validate like the server', () => {
  const draft = { ...validation.emptyInvoiceDraft('2026-11-18', 'CHF'), customerId: 'customer-a',
    items: [{ key: 'k', serviceId: null, description: 'A', quantity: '2', unit: 'PIECE', unitPrice: '10,50', taxRate: '8.1' }] }
  const input = validation.normalizeInvoiceDraft(draft)
  assert.equal(input.paymentTermDays, 30)
  assert.equal(input.items[0].unitPrice, '10.50')
  assert.equal(validation.dueDate(input), '2026-12-18')
  assert.equal(validation.dueDate({ ...input, paymentTermDays: 0 }), '2026-11-18')
  assert.deepEqual(validation.validateInvoice(input), {})
  const errors = validation.validateInvoice(validation.normalizeInvoiceDraft({ ...draft, customerId: '', paymentTermDays: '400', items: [{ ...draft.items[0], quantity: '0' }] }))
  assert.ok(errors.customerId); assert.ok(errors.paymentTermDays); assert.ok(errors['items[0].quantity'])
  assert.equal(validation.draftFromInvoice(invoice).paymentTermDays, '30')
})

test('payments must be positive, not in the future and never exceed the open amount', () => {
  const ok = validation.normalizePayment({ ...validation.emptyPayment('2026-11-18', '154.20') })
  assert.equal(ok.amount, '154.20')
  assert.deepEqual(validation.validatePayment(ok, '154.20', 'CHF', '2026-11-18'), {})
  assert.deepEqual(validation.validatePayment({ ...ok, amount: '0.05' }, '154.20', 'CHF', '2026-11-18'), {})
  assert.match(validation.validatePayment({ ...ok, amount: '154.21' }, '154.20', 'CHF', '2026-11-18').amount, /höher als der offene Betrag/)
  for (const amount of ['0', '-1', '1.005', 'abc', '']) assert.ok(validation.validatePayment({ ...ok, amount }, '154.20', 'CHF', '2026-11-18').amount, amount)
  assert.ok(validation.validatePayment({ ...ok, paymentDate: '2026-11-19' }, '154.20', 'CHF', '2026-11-18').paymentDate)
  assert.match(validation.validateReason('  '), /Grund/)
  assert.equal(validation.validateReason('Doppelt erfasst'), null)
})

test('capabilities follow the status rules', () => {
  const can = status => labels.invoiceCapabilities({ ...invoice, status })
  assert.deepEqual(can('DRAFT'), { edit: true, remove: true, issue: true, pay: false, cancel: false, reverse: false })
  assert.deepEqual(can('ISSUED'), { edit: false, remove: false, issue: false, pay: true, cancel: true, reverse: true })
  assert.deepEqual(can('PARTIALLY_PAID'), { edit: false, remove: false, issue: false, pay: true, cancel: false, reverse: true })
  assert.deepEqual(can('PAID'), { edit: false, remove: false, issue: false, pay: false, cancel: false, reverse: true })
  assert.deepEqual(can('CANCELLED'), { edit: false, remove: false, issue: false, pay: false, cancel: false, reverse: false })
  assert.match(labels.issueBlocker({ items: [], grandTotal: '0.00' }), /mindestens eine Position/)
  assert.match(labels.issueBlocker({ items: [{}], grandTotal: '0.00' }), /0.00/)
  assert.equal(labels.issueBlocker({ items: [{}], grandTotal: '10.00' }), null)
  assert.equal(labels.invoicePdfFilename('RE-0001'), 'Rechnung-RE-0001.pdf')
})

// ------------------------------------------------------------------ stores

function deferred() {
  let resolve
  const promise = new Promise(ok => { resolve = ok })
  return { promise, resolve }
}

test('stores drop late responses after a business switch or logout', async () => {
  const late = deferred()
  const list = invoiceListStore(B, { listInvoices: () => late.promise })
  const loading = list.load()
  list.clear()
  late.resolve([summary])
  await loading
  assert.deepEqual(list.getSnapshot().rows, [])

  const pending = deferred()
  const detail = invoiceDetailStore(B, 'invoice-a', { getInvoice: async () => invoice })
  await detail.load()
  assert.equal(detail.getSnapshot().invoice.invoiceNumber, 'RE-0001')
  const applying = detail.apply(() => pending.promise)
  detail.clear()
  pending.resolve({ ...invoice, status: 'PAID' })
  assert.equal(await applying, false)
  assert.equal(detail.getSnapshot().invoice, null)

  const editorLate = deferred()
  const editor = invoiceEditorStore(B, 'invoice-a', { getInvoice: () => editorLate.promise, listCustomers: async () => [], listServices: async () => [] })
  const reading = editor.load()
  editor.clear()
  editorLate.resolve(invoice)
  await reading
  assert.equal(editor.getSnapshot().invoice, null)
})
