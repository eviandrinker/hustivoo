import test from 'node:test'
import assert from 'node:assert/strict'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { renderModule } from './helpers/render-module.mjs'
const { ExpenseForm } = await import(await renderModule('expenses/ExpenseForm.tsx'))
const { FinanceOverview } = await import(await renderModule('pages/DashboardPage.tsx'))
const { PaymentPanel } = await import(await renderModule('expenses/PaymentPanel.tsx'))
const { Receipts } = await import(await renderModule('expenses/Receipts.tsx'))
const expense = { id: 'e', businessId: 'a', supplierId: 's', categoryId: 'c', description: 'Hosting', reference: null, expenseDate: '2026-01-10', dueDate: null, currency: 'CHF', netAmount: '100.00', taxRate: '8.10', taxAmount: '8.10', grossAmount: '108.10', inputTax: 'UNCHECKED', notes: null, status: 'UNPAID', payments: [], receipts: [] }
test('expense editor has accessible selections, amounts and explicitly unchecked input tax', () => {
  const html = renderToStaticMarkup(createElement(ExpenseForm, { suppliers: [{ id: 's', name: 'Supplier', archived: false }], categories: [{ id: 'c', name: 'Software', archived: false }], save: async () => {} }))
  for (const name of ['supplierId', 'categoryId', 'netAmount', 'taxRate', 'taxAmount', 'inputTax', 'dueDate']) assert.match(html, new RegExp(`name="${name}"`))
  assert.match(html, /Supplier/); assert.match(html, /Software/); assert.match(html, /UNCHECKED/); assert.doesNotMatch(html, /role="alert"/)
})
test('paid expenses lock historical inputs and expose payment reversal rather than overwriting', () => {
  const paid = { ...expense, status: 'PAID', payments: [{ id: 'p', amount: '108.10', currency: 'CHF', method: 'CARD', paymentDate: '2026-01-15', recordedAt: '2026-01-15T12:00:00Z', reversedAt: null }] }
  const form = renderToStaticMarkup(createElement(ExpenseForm, { expense: paid, suppliers: [], categories: [], save: async () => {} })); assert.match(form, /disabled=""/); assert.doesNotMatch(form, /Änderungen speichern/)
  const payment = renderToStaticMarkup(createElement(PaymentPanel, { expense: paid, apply: async () => {} })); assert.match(payment, /Zahlungsmarkierung zurücknehmen/); assert.doesNotMatch(payment, /Als bezahlt markieren/)
})
test('receipt panel supports multiple file selection and shows filename size and preview/download actions', () => {
  const row = { ...expense, receipts: [{ id: 'r', filename: 'receipt.pdf', contentType: 'application/pdf', sizeBytes: 1024 }] }
  const html = renderToStaticMarkup(createElement(Receipts, { business: 'a', expense: row, reload: async () => {} }))
  assert.match(html, /multiple=""/); assert.match(html, /receipt.pdf/); assert.match(html, /1 KB/); assert.match(html, /Anzeigen/); assert.match(html, /Download/)
})
test('dashboard renders real distinct metrics and explains its limits without fictitious profit', () => {
  const data = { totals: { invoiced: '200.00', outstanding: '150.00', received: '50.00', expenses: '108.10', paidExpenses: '108.10', openExpenses: '0.00' }, months: [{ month: '2026-01', received: '50.00', paidExpenses: '108.10' }] }
  const html = renderToStaticMarkup(createElement(FinanceOverview, { data })); assert.match(html, /CHF 200.00/); assert.match(html, /CHF 50.00/); assert.match(html, /Offene Forderungen/); assert.match(html, /keine Währungsumrechnung/); assert.doesNotMatch(html, /Demo workspace/)
  const empty = renderToStaticMarkup(createElement(FinanceOverview, { data: { ...data, months: [] } })); assert.match(empty, /Noch keine Zahlungen/)
})
