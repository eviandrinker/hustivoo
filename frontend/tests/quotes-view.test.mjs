import test from 'node:test'
import assert from 'node:assert/strict'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { renderModule } from './helpers/render-module.mjs'

const { QuoteResults } = await import(await renderModule('quotes/QuoteResults.tsx'))
const { QuoteEditorForm } = await import(await renderModule('quotes/QuoteEditorForm.tsx'))
const { QuoteActions, QuoteOverview } = await import(await renderModule('quotes/QuoteOverview.tsx'))

const noop = () => {}
const filters = { search: '', status: '' }
const row = { id: 'quote-a', businessId: 'business-a', customerId: 'customer-a', quoteNumber: 'ANG-0001', status: 'DRAFT', customerName: 'Muster AG', issueDate: '2026-11-18', validUntil: '2026-12-18', currency: 'CHF', grandTotal: '1234.50', updatedAt: '2026-11-18T10:00:00Z' }
const item = { id: 'item-1', serviceId: 'service-a', position: 1, description: 'Beratung\nWorkshop', quantity: '1.5', unit: 'HOUR', unitPrice: '95.10', taxRate: '8.10', lineSubtotal: '142.65', lineTax: '11.55', lineTotal: '154.20' }
const quote = {
  ...row, introductionText: 'Vielen Dank', closingText: 'Freundliche Grüsse', notes: 'Rabatt intern', subtotal: '142.65', taxTotal: '11.55', grandTotal: '154.20',
  customer: { customerNumber: 'K-0001', name: 'Muster AG', companyName: 'Muster AG', contactName: 'Erika Muster', street: 'Bahnhofstrasse 1', postalCode: '8001', city: 'Zürich', country: 'CH', email: 'erika@example.com' },
  items: [item], taxGroups: [{ rate: '8.10', base: '142.65', tax: '11.55' }], sentAt: null, decidedAt: null, createdAt: '2026-11-18T10:00:00Z',
}
const customers = [
  { id: 'customer-a', businessId: 'business-a', displayName: 'Muster AG', customerNumber: 'K-0001', archived: false },
  { id: 'customer-b', businessId: 'business-a', displayName: 'Beta GmbH', customerNumber: 'K-0002', archived: false },
  { id: 'customer-old', businessId: 'business-a', displayName: 'Alt AG', customerNumber: 'K-0003', archived: true },
]
const services = [
  { id: 'service-a', businessId: 'business-a', name: 'Beratung', description: null, category: null, unit: 'HOUR', unitPrice: '95.10', currency: 'CHF', taxRate: '8.10', active: true },
  { id: 'service-eur', businessId: 'business-a', name: 'Lizenz', description: null, category: null, unit: 'PIECE', unitPrice: '50.00', currency: 'EUR', taxRate: null, active: true },
]
function results(state, overrides = {}) {
  return renderToStaticMarkup(createElement(QuoteResults, { state, filters, today: '2026-11-18', downloading: null, onReload: noop, onReset: noop, onNew: noop, onOpen: noop, onEdit: noop, onDownload: noop, ...overrides }))
}
function editor(props = {}) {
  return renderToStaticMarkup(createElement(QuoteEditorForm, { customers, services, defaults: { today: '2026-11-18', currency: 'CHF' }, save: async () => {}, onCancel: noop, ...props }))
}

test('quote list renders loading, error, empty and filtered states without mock data', () => {
  const loading = results({ status: 'loading', rows: [] })
  assert.match(loading, /Angebote werden geladen/); assert.doesNotMatch(loading, /ANG-/)
  const error = results({ status: 'error', rows: [] })
  assert.match(error, /Angebote konnten nicht geladen werden/); assert.match(error, /Erneut laden/); assert.match(error, /role="alert"/)
  const empty = results({ status: 'ready', rows: [] })
  assert.match(empty, /Noch keine Angebote/); assert.match(empty, /Angebot erstellen/)
  const filtered = results({ status: 'ready', rows: [] }, { filters: { search: 'x', status: '' } })
  assert.match(filtered, /Keine passenden Angebote/); assert.match(filtered, /Filter zurücksetzen/)
  assert.match(results({ status: 'ready', rows: [] }, { filters: { search: '', status: 'SENT' } }), /Keine passenden Angebote/)
})

test('quote table shows number, customer, date, amount, status and status-dependent actions', () => {
  const html = results({ status: 'ready', rows: [row, { ...row, id: 'quote-b', quoteNumber: 'ANG-0002', status: 'SENT', validUntil: '2026-11-17' }, { ...row, id: 'quote-c', quoteNumber: 'ANG-0003', status: 'ACCEPTED' }] })
  for (const header of ['Angebotsnummer', 'Kunde', 'Datum', 'Betrag', 'Status', 'Aktionen']) assert.match(html, new RegExp(`<th[^>]*>${header}</th>`))
  assert.match(html, /href="\/quotes\/quote-a"[^>]*>ANG-0001</)
  assert.match(html, /Muster AG/); assert.match(html, /18\.11\.2026/); assert.match(html, /CHF 1’234\.50/)
  assert.match(html, /Entwurf/); assert.match(html, /Gesendet/); assert.match(html, /Angenommen/); assert.match(html, /Abgelaufen/)
  assert.match(html, /aria-label="ANG-0001 öffnen"/); assert.match(html, /aria-label="ANG-0001 bearbeiten"/)
  assert.doesNotMatch(html, /aria-label="ANG-0002 bearbeiten"/); assert.doesNotMatch(html, /aria-label="ANG-0003 bearbeiten"/)
  for (const number of ['ANG-0001', 'ANG-0002', 'ANG-0003']) assert.match(html, new RegExp(`aria-label="PDF von ${number} herunterladen"`))
  const busy = results({ status: 'ready', rows: [row] }, { downloading: 'quote-a' })
  assert.match(busy, /disabled=""[^>]*aria-label="PDF von ANG-0001 herunterladen"|aria-label="PDF von ANG-0001 herunterladen"[^>]*disabled=""/)
})

test('new quote editor offers only active customers and same-currency catalogue services', () => {
  const html = editor()
  for (const name of ['customerId', 'issueDate', 'validUntil', 'currency', 'catalogService', 'introductionText', 'closingText', 'notes']) assert.ok(html.includes(`name="${name}"`), name)
  assert.match(html, /<option value="customer-a">Muster AG · K-0001<\/option>/)
  assert.match(html, /<option value="customer-b">/)
  assert.doesNotMatch(html, /customer-old/)
  assert.match(html, /<option value="service-a">Beratung · CHF 95\.10 \/ Stunde<\/option>/)
  assert.match(html, /<option value="service-eur" disabled="">Lizenz · EUR 50\.00 \/ Stück \(nur EUR\)<\/option>/)
  assert.match(html, /value="2026-11-18"/); assert.match(html, /value="2026-12-18"/)
  assert.match(html, /Noch keine Positionen/)
  assert.match(html, /Freie Position/)
  assert.match(html, /Zusammenfassung/); assert.match(html, /CHF 0\.00/)
  assert.match(html, /Entwurf speichern/); assert.match(html, /Abbrechen/)
  assert.match(html, /erscheint nicht im PDF/)
  assert.doesNotMatch(html, /role="alert"/)
  const noCustomers = editor({ customers: [], services: [] })
  assert.match(noCustomers, /Lege zuerst einen Kunden an/); assert.match(noCustomers, /Keine aktiven Leistungen/)
})

test('editing a draft shows its positions with live totals and keeps an archived customer', () => {
  const second = { ...item, id: 'item-2', serviceId: null, position: 2, description: 'Reise', quantity: '1', unit: 'FIXED', unitPrice: '45.50', taxRate: '0.00', lineSubtotal: '45.50', lineTax: '0.00', lineTotal: '45.50' }
  const html = editor({ quote: { ...quote, customerId: 'customer-old-2', customer: { ...quote.customer, name: 'Archiviert AG', customerNumber: 'K-0009' }, items: [item, second], subtotal: '188.15', grandTotal: '199.70', taxGroups: [{ rate: '0.00', base: '45.50', tax: '0.00' }, { rate: '8.10', base: '142.65', tax: '11.55' }] } })
  assert.match(html, /<option value="customer-old-2" selected="">Archiviert AG · K-0009 \(archiviert\)<\/option>/)
  assert.match(html, /name="items\[0\]\.description"[^>]*>Beratung\nWorkshop<\/textarea>|>Beratung\nWorkshop<\/textarea>/)
  assert.match(html, /name="items\[0\]\.quantity"[^>]*value="1\.5"|value="1\.5"[^>]*name="items\[0\]\.quantity"/)
  assert.match(html, /name="items\[1\]\.unitPrice"/)
  assert.match(html, /Aus Leistungskatalog/); assert.match(html, /Freie Position/)
  assert.match(html, /CHF 142\.65/); assert.match(html, /CHF 45\.50/)
  assert.match(html, /MWST 8\.1 %/); assert.match(html, /CHF 11\.55/); assert.match(html, /CHF 199\.70/) // preview equals server totals
  assert.match(html, /aria-label="Position 1 nach oben" disabled=""|disabled=""[^>]*aria-label="Position 1 nach oben"/)
  assert.match(html, /aria-label="Position 2 nach unten" disabled=""|disabled=""[^>]*aria-label="Position 2 nach unten"/)
  assert.match(html, /aria-label="Position 2 entfernen"/)
  assert.match(html, /Änderungen speichern/)
  assert.match(html, /Rabatt intern/)
  const labels = [...html.matchAll(/<label for="([^"]+)">Menge<\/label>/g)].map(match => match[1])
  assert.equal(labels.length, 2)
  for (const id of labels) assert.match(html, new RegExp(`id="${id}"`))
})

test('detail actions follow the status rules', () => {
  const actions = status => renderToStaticMarkup(createElement(QuoteActions, { quote: { ...quote, status }, pending: false, downloading: false, onEdit: noop, onDelete: noop, onAction: noop, onDownload: noop }))
  const draft = actions('DRAFT')
  for (const label of ['Bearbeiten', 'PDF', 'Als gesendet markieren', 'Löschen']) assert.match(draft, new RegExp(label))
  assert.doesNotMatch(draft, /Angenommen|Abgelehnt/)
  const sent = actions('SENT')
  assert.match(sent, /Angenommen/); assert.match(sent, /Abgelehnt/); assert.match(sent, /PDF/)
  assert.doesNotMatch(sent, /Bearbeiten|Löschen|Als gesendet/)
  for (const status of ['ACCEPTED', 'REJECTED']) {
    const final = actions(status)
    assert.match(final, /PDF/); assert.doesNotMatch(final, /Bearbeiten|Löschen|Angenommen|Abgelehnt|Als gesendet/)
  }
  const busy = renderToStaticMarkup(createElement(QuoteActions, { quote, pending: false, downloading: true, onEdit: noop, onDelete: noop, onAction: noop, onDownload: noop }))
  assert.match(busy, /PDF wird erstellt/)
})

test('detail overview shows the document snapshot, server totals and internal notes separately', () => {
  const draft = renderToStaticMarkup(createElement(QuoteOverview, { quote, expired: false }))
  for (const text of ['Muster AG', 'Erika Muster', 'Bahnhofstrasse 1', '8001 Zürich', 'K-0001', '18.11.2026', '18.12.2026', 'Vielen Dank', 'Freundliche Grüsse', 'Beratung', 'Workshop', '1.5 Stunde', 'CHF 95.10', '8.1 %', 'CHF 142.65', 'CHF 154.20', 'Interne Notiz', 'Rabatt intern']) {
    assert.ok(draft.includes(text), text)
  }
  assert.doesNotMatch(draft, /schreibgeschützt/)
  const sent = renderToStaticMarkup(createElement(QuoteOverview, { quote: { ...quote, status: 'SENT', sentAt: '2026-11-18T11:00:00Z' }, expired: true }))
  assert.match(sent, /schreibgeschützt/); assert.match(sent, /Gültigkeit ist abgelaufen/); assert.match(sent, /Gesendet/)
  const accepted = renderToStaticMarkup(createElement(QuoteOverview, { quote: { ...quote, status: 'ACCEPTED', items: [] }, expired: false }))
  assert.match(accepted, /abgeschlossen und bleibt unverändert/); assert.match(accepted, /noch keine Positionen/)
})
