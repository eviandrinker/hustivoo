import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
async function moduleUrl(file, replacements = {}) {
  let source = await readFile(new URL('../src/' + file, import.meta.url), 'utf8')
  for (const [from, to] of Object.entries(replacements)) source = source.replaceAll(from, to)
  return `data:text/javascript;base64,${Buffer.from(ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2023, module: ts.ModuleKind.ESNext } }).outputText).toString('base64')}`
}
const clientUrl = await moduleUrl('api/client.ts')
const api = await import(await moduleUrl('api/expenses.ts', { "'./client'": JSON.stringify(clientUrl) }))
const { expenseStore } = await import(await moduleUrl('expenses/store.ts'))
const { validateExpense, validateSupplier, expensePreview, validateReceipt } = await import(await moduleUrl('expenses/validation.ts'))
const supplier = { id: 'supplier', businessId: 'a', name: 'Supplier', archived: false }
const category = { id: 'category', businessId: 'a', name: 'Software', archived: false }
const input = { supplierId: supplier.id, categoryId: category.id, description: 'Hosting', reference: null, expenseDate: '2026-01-10', dueDate: null, currency: 'CHF', netAmount: '100.00', taxRate: '8.10', taxAmount: null, inputTax: 'UNCHECKED', notes: null }
const expense = { ...input, id: 'expense', businessId: 'a', supplier, categoryName: 'Software', status: 'UNPAID', displayStatus: 'UNPAID', grossAmount: '108.10', taxAmount: '8.10', receipts: [], payments: [] }
const receipt = { id: 'receipt', businessId: 'a', expenseId: 'expense', filename: 'receipt.png', contentType: 'image/png', sizeBytes: 42 }
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
test('expense API sends search/date/supplier/category/status filters and validates totals', async t => {
  let called
  t.mock.method(globalThis, 'fetch', async path => { called = path; return json({ businessId: 'a', items: [expense], totals: { CHF: '108.10' }, monthTotals: {} }) })
  const filters = { search: 'A & B', from: '2026-01-01', to: '2026-12-31', supplierId: 'supplier', categoryId: 'category', status: 'UNPAID' }
  assert.equal((await api.listExpenses('a', filters)).items[0].id, 'expense')
  const params = new URL(called, 'http://localhost').searchParams; for (const [key, value] of Object.entries(filters)) assert.equal(params.get(key), value)
})
test('expense mutations preserve decimal strings and use CSRF without client supplied totals', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => { calls.push({ path, options }); return json(path.endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'token' } : expense) })
  await api.saveExpense('a', expense); await api.saveExpense('a', input, 'expense'); await api.payExpense('a', 'expense', '2026-01-15', 'CARD'); await api.reversePayment('a', 'expense', 'payment', 'Wrong date'); await api.cancelExpense('a', 'expense', 'Cancelled')
  const writes = calls.filter(x => x.options.method); assert.deepEqual(writes.map(x => x.options.method), ['POST', 'PUT', 'POST', 'PATCH', 'PATCH'])
  for (const call of writes) { assert.equal(call.options.credentials, 'same-origin'); assert.equal(call.options.headers.get('X-CSRF-TOKEN'), 'token') }
  const body = JSON.parse(writes[0].options.body); assert.equal(body.netAmount, '100.00'); assert.equal(body.grossAmount, undefined); assert.equal(body.status, undefined); assert.equal(body.businessId, undefined)
})
test('responses from foreign tenants, wrong receipt parents and malformed monetary values are rejected', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => json({ ...expense, businessId: 'b' }))
  await assert.rejects(api.getExpense('a', 'expense'))
  for (const altered of [{ ...expense, grossAmount: 108.10 }, { ...expense, supplier: { ...supplier, businessId: 'b' } }, { ...expense, receipts: [{ ...receipt, expenseId: 'other' }] }]) { mock.mock.mockImplementation(async () => json(altered)); await assert.rejects(api.getExpense('a', 'expense')) }
})
test('supplier and category APIs remain scoped and support archive and restore', async t => {
  const calls = []
  t.mock.method(globalThis, 'fetch', async (path, options) => {
    calls.push({ path, options }); if (path.endsWith('/csrf')) return json({ headerName: 'X-CSRF-TOKEN', token: 'token' })
    const value = path.includes('suppliers') ? supplier : category
    return json(options.method ? value : [value])
  })
  assert.equal((await api.listSuppliers('a', 'Sup', true))[0].name, 'Supplier')
  assert.equal((await api.listCategories('a'))[0].name, 'Software')
  await api.saveSupplier('a', supplier); await api.archiveSupplier('a', 'supplier', true); await api.archiveSupplier('a', 'supplier', false)
  await api.saveCategory('a', 'Software'); await api.archiveCategory('a', 'category', true)
  assert.ok(calls.some(x => x.path.endsWith('/supplier/archive'))); assert.ok(calls.some(x => x.path.endsWith('/supplier/restore')))
})
test('exact amount preview uses HALF_UP and permits an explicit receipt tax without floating point', () => {
  assert.deepEqual(expensePreview('100.00', '8.10', null), { tax: '8.10', gross: '108.10' })
  assert.deepEqual(expensePreview('0.50', '8.10', null), { tax: '0.04', gross: '0.54' })
  assert.deepEqual(expensePreview('100.00', null, null), { tax: '0.00', gross: '100.00' })
  assert.deepEqual(expensePreview('100.00', '8.10', '8.12'), { tax: '8.12', gross: '108.12' })
  assert.deepEqual(expensePreview('99999999999999999.99', null, null), { tax: '0.00', gross: '99999999999999999.99' })
  assert.equal(expensePreview('-1', null, null), null)
})
test('expense and supplier validation catches missing selections and invalid amounts/dates', () => {
  assert.deepEqual(validateExpense(input), {})
  for (const [key, value] of Object.entries({ description: '', supplierId: '', categoryId: '', netAmount: '-1', dueDate: '2025-01-01', taxRate: '101', currency: 'XX' })) assert.ok(validateExpense({ ...input, [key]: value })[key])
  assert.ok(validateSupplier({ name: '', email: 'invalid' }).name)
  assert.ok(validateSupplier({ name: 'Supplier', email: 'invalid' }).email)
})
test('upload preflight rejects unsupported and oversized receipts', () => {
  assert.equal(validateReceipt({ size: 1024, type: 'application/pdf' }), null)
  assert.equal(validateReceipt({ size: 1024, type: 'image/png' }), null)
  assert.ok(validateReceipt({ size: 1024, type: 'text/html' }))
  assert.ok(validateReceipt({ size: 10485761, type: 'image/jpeg' }))
})
test('receipt upload reports real progress and reuses the shared session CSRF client', async t => {
  t.mock.method(globalThis, 'fetch', async () => json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }))
  const original = globalThis.XMLHttpRequest; let sent
  class Xhr {
    upload = {}; headers = {}; status = 201; responseText = JSON.stringify(receipt)
    open(method, path) { this.method = method; this.path = path }
    setRequestHeader(key, value) { this.headers[key] = value }
    getResponseHeader() { return 'application/json' }
    send(body) { sent = this; this.body = body; this.upload.onprogress({ lengthComputable: true, loaded: 5, total: 10 }); this.onload(); this.onloadend() }
  }
  globalThis.XMLHttpRequest = Xhr; t.after(() => { globalThis.XMLHttpRequest = original })
  const progress = []; const result = await api.uploadReceipt('a', 'expense', new File(['test'], 'test.png', { type: 'image/png' }), n => progress.push(n))
  assert.equal(result.id, 'receipt'); assert.deepEqual(progress, [50]); assert.equal(sent.headers['X-CSRF-TOKEN'], 'csrf'); assert.equal(sent.path, '/api/businesses/a/expenses/expense/receipts'); assert.ok(sent.body.get('file') instanceof File)
})
test('receipt binary preview uses authenticated request and rejects unexpected content types', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async (_path, options) => { assert.equal(options.credentials, 'same-origin'); return new Response(new Uint8Array([1, 2]), { headers: { 'Content-Type': 'image/png' } }) })
  assert.equal((await api.receiptBlob('a', 'expense', receipt)).size, 2)
  mock.mock.mockImplementation(async () => new Response('<html>', { headers: { 'Content-Type': 'text/html' } }))
  await assert.rejects(api.receiptBlob('a', 'expense', receipt))
})
test('loading/error/empty are distinct and retry restores data', async () => {
  let fail = false
  const store = expenseStore(async () => { if (fail) throw new Error(); return [] })
  assert.equal(store.getSnapshot().status, 'loading'); await store.load(); assert.equal(store.getSnapshot().status, 'ready'); assert.deepEqual(store.getSnapshot().data, [])
  fail = true; await store.load(); assert.equal(store.getSnapshot().status, 'error'); assert.equal(store.getSnapshot().data, null)
  fail = false; await store.load(); assert.equal(store.getSnapshot().status, 'ready')
})
test('business switching and logout clear all data and ignore late reads and writes', async () => {
  let finish; const store = expenseStore(() => new Promise(resolve => { finish = resolve }))
  const loading = store.load(); store.clear(); finish([expense]); await loading; assert.equal(store.getSnapshot().data, null)
  let done; const mutation = store.run(() => new Promise(resolve => { done = resolve })); store.clear(); done(expense); assert.equal(await mutation, undefined)
})
test('latest request wins and failed mutations are never replayed', async () => {
  const pending = []; const store = expenseStore(() => new Promise(resolve => pending.push(resolve)))
  const a = store.load(); const b = store.load(); pending[1](['new']); await b; pending[0](['old']); await a; assert.deepEqual(store.getSnapshot().data, ['new'])
  let attempts = 0; await assert.rejects(store.run(async () => { attempts++; throw new Error() })); assert.equal(attempts, 1)
})
test('finance parser validates tenant, currency and exact totals', async t => {
  const data = { businessId: 'a', currency: 'CHF', from: '2026-01-01', to: '2026-12-31', totals: { invoiced: '200.00', outstanding: '150.00', received: '50.00', expenses: '108.10', openExpenses: '0.00', paidExpenses: '108.10' }, months: [{ month: '2026-02', received: '50.00', paidExpenses: '108.10' }] }
  const mock = t.mock.method(globalThis, 'fetch', async () => json(data)); assert.deepEqual(await api.financeOverview('a', data.from, data.to), data)
  mock.mock.mockImplementation(async () => json({ ...data, businessId: 'b' })); await assert.rejects(api.financeOverview('a', data.from, data.to))
})
