import test from 'node:test'
import assert from 'node:assert/strict'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { renderModule } from './helpers/render-module.mjs'
import { invoice, payment, summary } from './helpers/invoice-fixture.mjs'

const { InvoiceResults } = await import(await renderModule('invoices/InvoiceResults.tsx'))
const { InvoiceEditorForm } = await import(await renderModule('invoices/InvoiceEditorForm.tsx'))
const { InvoiceActions, InvoiceOverview, PaymentForm, ReasonForm } = await import(await renderModule('invoices/InvoiceOverview.tsx'))
const { QuoteActions } = await import(await renderModule('quotes/QuoteOverview.tsx'))

const noop = () => {}
const filters = { search: '', status: '' }
const routed = element => renderToStaticMarkup(createElement(MemoryRouter, null, element))
const results = (state, overrides = {}) => renderToStaticMarkup(createElement(InvoiceResults, { state, filters, downloading: null, onReload: noop, onReset: noop, onNew: noop, onOpen: noop, onEdit: noop, onDownload: noop, ...overrides }))
const customers = [{ id: 'customer-a', businessId: 'business-a', displayName: 'Muster AG', customerNumber: 'K-0001', archived: false }]
const services = [{ id: 'service-a', businessId: 'business-a', name: 'Beratung', description: null, category: null, unit: 'HOUR', unitPrice: '95.10', currency: 'CHF', taxRate: '8.10', active: true }]

test('invoice list renders loading, error, empty and filtered states', () => {
  assert.match(results({ status: 'loading', rows: [] }), /Rechnungen werden geladen/)
  assert.match(results({ status: 'error', rows: [] }), /Erneut laden/)
  const empty = results({ status: 'ready', rows: [] })
  assert.match(empty, /Noch keine Rechnungen/); assert.match(empty, /Neue Rechnung/)
  assert.match(results({ status: 'ready', rows: [] }, { filters: { search: '', status: 'OVERDUE' } }), /Keine passenden Rechnungen/)
})

test('invoice table shows number, customer, dates, total, open amount and status', () => {
  const html = results({ status: 'ready', rows: [
    summary,
    { ...summary, id: 'invoice-b', invoiceNumber: 'RE-0002', status: 'ISSUED', displayStatus: 'OVERDUE', overdue: true, dueDate: '2026-11-01' },
    { ...summary, id: 'invoice-c', invoiceNumber: 'RE-0003', status: 'DRAFT', displayStatus: 'DRAFT', openAmount: '0.00' },
    { ...summary, id: 'invoice-d', invoiceNumber: 'RE-0004', status: 'PARTIALLY_PAID', displayStatus: 'PARTIALLY_PAID', paidTotal: '50.00', openAmount: '104.20' }] })
  for (const header of ['Rechnungsnummer', 'Kunde', 'Datum', 'Fällig', 'Total', 'Offen', 'Status', 'Aktionen']) assert.match(html, new RegExp(`<th[^>]*>${header}</th>`))
  assert.match(html, /href="\/invoices\/invoice-a"[^>]*>RE-0001</)
  assert.match(html, /18\.12\.2026/); assert.match(html, /CHF 154\.20/); assert.match(html, /CHF 104\.20/)
  for (const label of ['Offen', 'Überfällig', 'Entwurf', 'Teilweise bezahlt']) assert.match(html, new RegExp(label))
  assert.match(html, /class="invoice-overdue"[^>]*>01\.11\.2026/)
  assert.match(html, /aria-label="RE-0003 bearbeiten"/); assert.doesNotMatch(html, /aria-label="RE-0001 bearbeiten"/)
})

test('editor offers customers, payment terms with due date, catalogue and live totals', () => {
  const html = renderToStaticMarkup(createElement(InvoiceEditorForm, { customers, services, defaults: { today: '2026-11-18', currency: 'CHF' }, save: async () => {}, onCancel: noop }))
  for (const name of ['customerId', 'issueDate', 'paymentTermDays', 'currency', 'catalogService', 'introductionText', 'closingText', 'notes']) assert.ok(html.includes(`name="${name}"`), name)
  assert.match(html, /<option value="30" selected="">30 Tage<\/option>/)
  assert.match(html, /<option value="0">Sofort<\/option>/)
  assert.match(html, /Fällig am 18\.12\.2026/)
  assert.match(html, /Kunde und Zahlungsbedingungen/)
  assert.match(html, /<option value="service-a">Beratung · CHF 95\.10 \/ Stunde<\/option>/)
  assert.match(html, /id="invoice-add-free"/)
  assert.match(html, /Entwurf speichern/)
  const edit = renderToStaticMarkup(createElement(InvoiceEditorForm, { invoice: { ...invoice, status: 'DRAFT' }, customers, services, defaults: { today: '2026-11-18', currency: 'CHF' }, save: async () => {}, onCancel: noop }))
  assert.match(edit, />Beratung\nWorkshop<\/textarea>/); assert.match(edit, /CHF 154\.20/); assert.match(edit, /Änderungen speichern/)
  assert.match(renderToStaticMarkup(createElement(InvoiceEditorForm, { customers, services, defaults: { today: '2026-11-18', currency: 'USD' }, save: async () => {}, onCancel: noop })), /QR-Zahlteil nur für CHF und EUR/)
})

test('actions follow the invoice status', () => {
  const actions = status => renderToStaticMarkup(createElement(InvoiceActions, { invoice: { ...invoice, status }, pending: false, downloading: false, onCommand: noop }))
  const draft = actions('DRAFT')
  for (const label of ['Bearbeiten', 'PDF', 'Rechnung ausstellen', 'Löschen']) assert.match(draft, new RegExp(label))
  assert.doesNotMatch(draft, /Zahlung erfassen|Stornieren/)
  const issued = actions('ISSUED')
  for (const label of ['PDF', 'Zahlung erfassen', 'Stornieren']) assert.match(issued, new RegExp(label))
  assert.doesNotMatch(issued, /Bearbeiten|Löschen|ausstellen/)
  assert.doesNotMatch(actions('PARTIALLY_PAID'), /Stornieren/)
  for (const status of ['PAID', 'CANCELLED']) assert.doesNotMatch(actions(status), /Zahlung erfassen|Stornieren|Bearbeiten/)
})

test('detail shows balances, payment history with reversals and the quote link', () => {
  const partial = { ...invoice, status: 'PARTIALLY_PAID', displayStatus: 'PARTIALLY_PAID', paidTotal: '50.00', openAmount: '104.20',
    payments: [payment, { ...payment, id: 'payment-2', amount: '20.00', reversed: true, reversedAt: '2026-11-21T08:00:00Z', reversalReason: 'Doppelt gebucht' }] }
  const html = routed(createElement(InvoiceOverview, { invoice: partial, pending: false, onReverse: noop }))
  for (const text of ['Muster AG', 'Bahnhofstrasse 1', '3000 Bern', 'K-0001', '30 Tage', '18.12.2026', 'ANG-0007', 'Beratung', 'CHF 154.20', 'Bezahlt', 'CHF 50.00', 'Offen', 'CHF 104.20',
    'Banküberweisung', 'Auszug 4', 'Doppelt gebucht', 'Mahnung intern', 'QR-Zahlteil über CHF 154.20']) assert.ok(html.includes(text), text)
  assert.match(html, /href="\/quotes\/quote-a"/)
  assert.equal([...html.matchAll(/>Stornieren</g)].length, 1)             // the reversed payment cannot be reversed again
  const overdue = routed(createElement(InvoiceOverview, { invoice: { ...invoice, overdue: true, displayStatus: 'OVERDUE' }, pending: false, onReverse: noop }))
  assert.match(overdue, /Zahlungsfrist ist am 18\.12\.2026 abgelaufen/)
  const cancelled = routed(createElement(InvoiceOverview, { invoice: { ...invoice, status: 'CANCELLED', displayStatus: 'CANCELLED', cancellationReason: 'Doppelt', qrBill: { available: false, problems: ['x'] } }, pending: false, onReverse: noop }))
  assert.match(cancelled, /storniert: «Doppelt»/); assert.doesNotMatch(cancelled, /Ohne QR-Zahlteil|Offen</)
})

test('missing QR prerequisites are explained with a link to the settings', () => {
  const html = routed(createElement(InvoiceOverview, { invoice: { ...invoice, status: 'DRAFT', displayStatus: 'DRAFT', qrBill: { available: false, problems: ['In den Unternehmenseinstellungen ist keine IBAN hinterlegt.'] } }, pending: false, onReverse: noop }))
  assert.match(html, /Ohne QR-Zahlteil/); assert.match(html, /keine IBAN hinterlegt/); assert.match(html, /href="\/settings"/)
  const draftPreview = routed(createElement(InvoiceOverview, { invoice: { ...invoice, status: 'DRAFT', displayStatus: 'DRAFT' }, pending: false, onReverse: noop }))
  assert.match(draftPreview, /NICHT ZUR ZAHLUNG VERWENDEN/)
})

test('payment dialog content prefills the open amount; reason forms require a reason field', () => {
  const html = renderToStaticMarkup(createElement(PaymentForm, { invoice: { ...invoice, openAmount: '104.20' }, today: '2026-11-18', save: async () => {}, onCancel: noop }))
  for (const name of ['paymentDate', 'amount', 'method', 'reference', 'note']) assert.ok(html.includes(`name="${name}"`), name)
  assert.match(html, /name="amount" value="104\.20"/); assert.match(html, /max="2026-11-18"/)
  assert.match(html, /<option value="QR_BILL">QR-Rechnung<\/option>/); assert.match(html, /Zahlung buchen/)
  const reason = renderToStaticMarkup(createElement(ReasonForm, { label: 'Grund der Stornierung', confirm: 'Stornieren', save: async () => {}, onCancel: noop }))
  assert.match(reason, /Grund der Stornierung/); assert.match(reason, /name="reason"/)
})

test('accepted quotes offer "Rechnung erstellen" or link the existing invoice', () => {
  const quote = { id: 'quote-a', quoteNumber: 'ANG-0007', status: 'ACCEPTED', invoice: null, items: [] }
  const props = { pending: false, downloading: false, onEdit: noop, onDelete: noop, onAction: noop, onDownload: noop, onInvoice: noop }
  assert.match(routed(createElement(QuoteActions, { ...props, quote })), /Rechnung erstellen/)
  const linked = routed(createElement(QuoteActions, { ...props, quote: { ...quote, invoice: { id: 'invoice-a', invoiceNumber: 'RE-0001' } } }))
  assert.match(linked, /href="\/invoices\/invoice-a"[^>]*>.*Rechnung RE-0001/); assert.doesNotMatch(linked, /Rechnung erstellen/)
  assert.doesNotMatch(routed(createElement(QuoteActions, { ...props, quote: { ...quote, status: 'SENT' } })), /Rechnung/)
})
