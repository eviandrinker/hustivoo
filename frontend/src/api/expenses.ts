import { requestJson, requestBlob, uploadFile } from './client'
export type ExpenseStatus = 'UNPAID' | 'PAID' | 'CANCELLED' | 'OVERDUE'
export type InputTax = 'UNCHECKED' | 'DEDUCTIBLE' | 'NOT_DEDUCTIBLE' | 'PARTIAL'
export const paymentMethods = { BANK_TRANSFER: 'Banküberweisung', QR_BILL: 'QR-Rechnung', CASH: 'Bar', CARD: 'Karte', TWINT: 'TWINT', OTHER: 'Andere' } as const
export interface SupplierInput { name: string; contactPerson: string | null; email: string | null; phone: string | null; street: string | null; houseNumber: string | null; postalCode: string | null; city: string | null; country: string; vatNumber: string | null; iban: string | null; notes: string | null }
export interface Supplier extends SupplierInput { id: string; businessId: string; archived: boolean }
export interface Category { id: string; businessId: string; name: string; archived: boolean }
export interface ExpenseInput { supplierId: string; categoryId: string; description: string; reference: string | null; expenseDate: string; dueDate: string | null; currency: string; netAmount: string; taxRate: string | null; taxAmount: string | null; inputTax: InputTax; notes: string | null }
export interface Receipt { id: string; businessId: string; expenseId: string; filename: string; contentType: string; sizeBytes: number; createdAt: string }
export interface ExpensePayment { id: string; paymentDate: string; method: keyof typeof paymentMethods; amount: string; currency: string; recordedAt: string; reversedAt: string | null; reversalReason: string | null }
export interface Expense extends ExpenseInput { id: string; businessId: string; supplier: Supplier; categoryName: string; status: Exclude<ExpenseStatus, 'OVERDUE'>; displayStatus: ExpenseStatus; grossAmount: string; taxAmount: string; receiptCount?: number; payments?: ExpensePayment[]; receipts?: Receipt[]; cancellationReason: string | null; createdAt: string; updatedAt: string }
export interface ExpenseFilters { search: string; from: string; to: string; categoryId: string; supplierId: string; status: ExpenseStatus | '' }
export const defaultFilters: ExpenseFilters = { search: '', from: '', to: '', categoryId: '', supplierId: '', status: '' }
export interface ExpenseList { businessId: string; items: Expense[]; totals: Record<string, string>; monthTotals: Record<string, string> }
export interface Finance { businessId: string; currency: string; from: string; to: string; totals: Record<'invoiced' | 'outstanding' | 'received' | 'expenses' | 'openExpenses' | 'paidExpenses', string>; months: { month: string; received: string; paidExpenses: string }[] }
const money = (x: unknown) => typeof x === 'string' && /^\d+\.\d{2}$/.test(x)
function record(value: unknown): Record<string, unknown> { if (typeof value !== 'object' || value === null || Array.isArray(value)) throw new Error('Unexpected response'); return value as Record<string, unknown> }
function check(condition: boolean) { if (!condition) throw new Error('Unexpected response') }
function scoped(value: unknown, business: string, id?: string) { const r = record(value); check(r.businessId === business && typeof r.id === 'string' && Boolean(r.id) && (!id || r.id === id)); return r }
function supplier(value: unknown, business: string) { const r = scoped(value, business); check(typeof r.name === 'string' && typeof r.archived === 'boolean'); return r as unknown as Supplier }
function category(value: unknown, business: string) { const r = scoped(value, business); check(typeof r.name === 'string' && typeof r.archived === 'boolean'); return r as unknown as Category }
function receipt(value: unknown, business: string, expense: string) { const r = scoped(value, business); check(r.expenseId === expense && typeof r.filename === 'string' && ['application/pdf', 'image/png', 'image/jpeg'].includes(String(r.contentType)) && typeof r.sizeBytes === 'number' && r.sizeBytes > 0); return r as unknown as Receipt }
function expense(value: unknown, business: string, id?: string): Expense {
  const r = scoped(value, business, id)
  check(typeof r.description === 'string' && typeof r.supplierId === 'string' && typeof r.categoryId === 'string' && typeof r.categoryName === 'string' && typeof r.expenseDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(r.expenseDate) && typeof r.currency === 'string' && /^[A-Z]{3}$/.test(r.currency) && ['netAmount', 'taxAmount', 'grossAmount'].every(key => money(r[key])) && ['UNPAID', 'PAID', 'CANCELLED'].includes(String(r.status)) && ['UNPAID', 'PAID', 'CANCELLED', 'OVERDUE'].includes(String(r.displayStatus)))
  r.supplier = supplier(r.supplier, business)
  if (r.receipts !== undefined) { check(Array.isArray(r.receipts)); r.receipts = (r.receipts as unknown[]).map(x => receipt(x, business, String(r.id))) }
  if (r.payments !== undefined) { check(Array.isArray(r.payments)); for (const item of r.payments as unknown[]) { const p = scoped(item, business); check(p.expenseId === r.id && money(p.amount) && typeof p.paymentDate === 'string' && Object.hasOwn(paymentMethods, String(p.method))) } }
  return r as unknown as Expense
}
function list<T>(value: unknown, parse: (x: unknown) => T): T[] { check(Array.isArray(value)); return (value as unknown[]).map(parse) }
export const base = (business: string) => `/api/businesses/${encodeURIComponent(business)}`
const path = (business: string, id: string) => `${base(business)}/expenses/${encodeURIComponent(id)}`
function write(url: string, method: string, body?: unknown) { return requestJson(url, { method, ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) }) }
export async function listSuppliers(b: string, search = '', archived = false) { return list(await requestJson(`${base(b)}/suppliers?${new URLSearchParams({ search, archived: String(archived) })}`), x => supplier(x, b)) }
export async function saveSupplier(b: string, input: SupplierInput, id?: string) { return supplier(await write(`${base(b)}/suppliers${id ? '/' + encodeURIComponent(id) : ''}`, id ? 'PUT' : 'POST', input), b) }
export async function archiveSupplier(b: string, id: string, archived: boolean) { return supplier(await write(`${base(b)}/suppliers/${encodeURIComponent(id)}/${archived ? 'archive' : 'restore'}`, 'PATCH'), b) }
export async function listCategories(b: string) { return list(await requestJson(`${base(b)}/expense-categories`), x => category(x, b)) }
export async function defaultCategories(b: string) { return list(await write(`${base(b)}/expense-categories/defaults`, 'POST'), x => category(x, b)) }
export async function saveCategory(b: string, name: string, id?: string) { return category(await write(`${base(b)}/expense-categories${id ? '/' + encodeURIComponent(id) : ''}`, id ? 'PUT' : 'POST', { name }), b) }
export async function archiveCategory(b: string, id: string, archived: boolean) { return category(await write(`${base(b)}/expense-categories/${encodeURIComponent(id)}/${archived ? 'archive' : 'restore'}`, 'PATCH'), b) }
export async function listExpenses(b: string, filters: ExpenseFilters = defaultFilters): Promise<ExpenseList> {
  const params = new URLSearchParams(); for (const [key, value] of Object.entries(filters)) if (value) params.set(key, value)
  const data = record(await requestJson(`${base(b)}/expenses?${params}`)); check(data.businessId === b)
  for (const key of ['totals', 'monthTotals']) check(Object.values(record(data[key])).every(money))
  return { ...data, items: list(data.items, x => expense(x, b)) } as unknown as ExpenseList
}
export async function getExpense(b: string, id: string) { return expense(await requestJson(path(b, id)), b, id) }
export async function saveExpense(b: string, input: ExpenseInput, id?: string) {
  const { supplierId, categoryId, description, reference, expenseDate, dueDate, currency, netAmount, taxRate, taxAmount, inputTax, notes } = input
  return expense(await write(id ? path(b, id) : `${base(b)}/expenses`, id ? 'PUT' : 'POST', { supplierId, categoryId, description, reference, expenseDate, dueDate, currency, netAmount, taxRate, taxAmount, inputTax, notes }), b, id)
}
export async function cancelExpense(b: string, id: string, reason: string) { return expense(await write(`${path(b, id)}/cancel`, 'PATCH', { reason }), b, id) }
export async function payExpense(b: string, id: string, paymentDate: string, method: keyof typeof paymentMethods) { return expense(await write(`${path(b, id)}/payments`, 'POST', { paymentDate, method }), b, id) }
export async function reversePayment(b: string, id: string, payment: string, reason: string) { return expense(await write(`${path(b, id)}/payments/${encodeURIComponent(payment)}/reverse`, 'PATCH', { reason }), b, id) }
export async function deleteExpense(b: string, id: string) { await write(path(b, id), 'DELETE') }
export async function uploadReceipt(b: string, id: string, file: File, progress: (n: number) => void, signal?: AbortSignal) { return receipt(await uploadFile(`${path(b, id)}/receipts`, file, progress, signal), b, id) }
export async function removeReceipt(b: string, id: string, receipt: string) { await write(`${path(b, id)}/receipts/${encodeURIComponent(receipt)}`, 'DELETE') }
export function receiptBlob(b: string, id: string, receipt: Receipt, signal?: AbortSignal) { return requestBlob(`${path(b, id)}/receipts/${encodeURIComponent(receipt.id)}`, receipt.contentType, signal) }
export async function financeOverview(b: string, from: string, to: string): Promise<Finance> {
  const r = record(await requestJson(`${base(b)}/finance/overview?${new URLSearchParams({ from, to, currency: 'CHF' })}`))
  check(r.businessId === b && r.currency === 'CHF' && r.from === from && r.to === to)
  const totals = record(r.totals); check(['invoiced', 'outstanding', 'received', 'expenses', 'openExpenses', 'paidExpenses'].every(key => money(totals[key])))
  check(Array.isArray(r.months)); for (const item of r.months as unknown[]) { const month = record(item); check(typeof month.month === 'string' && money(month.received) && money(month.paidExpenses)) }
  return r as unknown as Finance
}
