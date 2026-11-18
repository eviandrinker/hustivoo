import test from 'node:test'
import assert from 'node:assert/strict'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { renderModule } from './helpers/render-module.mjs'
const { ServiceResults } = await import(await renderModule('services/ServiceResults.tsx'))
const { ServiceForm } = await import(await renderModule('services/ServiceForm.tsx'))
const filters = { search: '', category: '', active: true, sort: 'NAME_ASC' }
const row = { id: 'a', businessId: 'a', name: 'Beratung', description: null, category: 'Consulting', unitPrice: '95.10', unit: 'HOUR', active: true, currency: 'CHF', taxRate: null }
const noop = () => {}
function results(state, overrides = {}) {
  return renderToStaticMarkup(createElement(ServiceResults, { state, filters, pending: false, onReload: noop, onReset: noop, onNew: noop, onEdit: noop, onArchive: noop, ...overrides }))
}
test('rendered catalogue distinguishes loading, errors, empty, filtered and archived lists', () => {
  const loading = results({ status: 'loading', rows: [] })
  assert.match(loading, /Leistungen werden geladen/); assert.doesNotMatch(loading, /Dein Leistungskatalog beginnt/)
  const error = results({ status: 'error', rows: [] })
  assert.match(error, /konnten nicht geladen/); assert.match(error, /Erneut laden/); assert.doesNotMatch(error, /Dein Leistungskatalog beginnt/)
  const empty = results({ status: 'ready', rows: [] })
  assert.match(empty, /Dein Leistungskatalog beginnt/); assert.match(empty, /Leistung hinzufügen/)
  assert.match(results({ status: 'ready', rows: [] }, { filters: { ...filters, search: 'missing' } }), /Keine passenden Leistungen/)
  const archived = results({ status: 'ready', rows: [] }, { filters: { ...filters, active: false } })
  assert.match(archived, /Keine archivierten Leistungen/); assert.doesNotMatch(archived, /Leistung hinzufügen/)
})
test('table renders real prices, units and accessible edit/archive/restore actions', () => {
  const table = results({ status: 'ready', rows: [row] })
  assert.match(table, /CHF 95.10/); assert.match(table, /Stunde/); assert.match(table, /Consulting/)
  assert.match(table, /aria-label="Beratung bearbeiten"/); assert.match(table, /aria-label="Beratung archivieren"/)
  const archived = results({ status: 'ready', rows: [{ ...row, active: false }] }, { pending: true })
  assert.match(archived, /Wiederherstellen/); assert.match(archived, /disabled=""/)
})
test('create and edit forms render named accessible fields and preserve exact decimal prices', () => {
  const create = renderToStaticMarkup(createElement(ServiceForm, { save: async () => {} }))
  for (const name of ['name', 'description', 'category', 'unit', 'unitPrice', 'currency', 'taxRate']) assert.ok(create.includes(`name="${name}"`))
  assert.match(create, /value="CHF"/); assert.match(create, /Leistung erstellen/)
  assert.doesNotMatch(create, /role="alert"/)
  const edit = renderToStaticMarkup(createElement(ServiceForm, { service: { ...row, unitPrice: '99999999999999999.99' }, save: async () => {} }))
  assert.match(edit, /value="99999999999999999.99"/); assert.match(edit, /Änderungen speichern/)
})

test('editor separates sections and footer and shows the selected currency beside the exact price', () => {
  const html = renderToStaticMarkup(createElement(ServiceForm, { service: { ...row, currency: 'EUR', unitPrice: '100.00' }, onCancel: noop, save: async () => {} }))
  assert.match(html, /<legend>Allgemein<\/legend>/)
  assert.match(html, /<legend>Preisgestaltung<\/legend>/)
  assert.ok(html.indexOf('name="category"') < html.indexOf('name="description"'))
  assert.match(html, /rows="3"/)
  assert.match(html, /service-price-input.*?aria-hidden="true">EUR<\/span>/)
  assert.match(html, /value="100.00"/)
  assert.match(html, /service-form-footer.*?type="button".*?Abbrechen.*?type="submit"/)
  assert.doesNotMatch(html, /value="8.10"/)
  const price = html.match(/<input[^>]*name="unitPrice"[^>]*>/)?.[0]
  const id = price?.match(/id="([^"]+)"/)?.[1]
  assert.ok(id && html.includes(`for="${id}"`), 'price label targets the actual input')
})

test('row actions use a labelled native popover with a keyboard accessible trigger', () => {
  const html = results({ status: 'ready', rows: [row] })
  assert.match(html, /popovertarget="[^"]+"/i)
  assert.match(html, /popover="auto"/)
  assert.match(html, /aria-label="Aktionen für Beratung"/)
  assert.match(html, /role="group"/)
  assert.doesNotMatch(html, /<th[^>]*>Einheit<\/th>/)
})

test('dialog icon has an accessible name and disables dismissal during saving', async () => {
  const { Dialog } = await import(await renderModule('components/ui/dialog.tsx'))
  const html = renderToStaticMarkup(createElement(Dialog, { open: true, iconClose: true, closeDisabled: true, className: 'service-dialog', onClose: noop, title: 'Neue Leistung', description: 'Definiere deine Dienstleistung und ihren Preis.' }))
  assert.match(html, /aria-label="Dialog schliessen"/)
  assert.match(html, /disabled=""/)
  assert.doesNotMatch(html, />Close</)
  assert.match(html, /aria-labelledby=/)
  assert.match(html, /aria-describedby=/)
})
