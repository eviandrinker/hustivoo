import { useRef, useState } from 'react'
import * as api from '@/api/expenses'
import { Button } from '@/components/ui/button'
import { FormField, Input, Select } from '@/components/ui/forms'
import { localToday } from '@/quotes/validation'
import { formatPrice } from '@/services/validation'
export function PaymentPanel({ expense, apply }: { expense: api.Expense; apply: (operation: () => Promise<api.Expense>) => Promise<void> }) {
  const [date, setDate] = useState(localToday); const [method, setMethod] = useState<keyof typeof api.paymentMethods>('BANK_TRANSFER'); const [reason, setReason] = useState('')
  const [error, setError] = useState(''); const [pending, setPending] = useState(false); const busy = useRef(false)
  async function run(operation: () => Promise<api.Expense>) { if (busy.current) return; busy.current = true; setPending(true); setError(''); try { await apply(operation) } catch { setError('Änderung nicht bestätigt. Bitte prüfe den aktuellen Stand vor einem weiteren Versuch.') } finally { busy.current = false; setPending(false) } }
  return <section className="surface expense-panel"><h2>Zahlung & Verlauf</h2>{error && <p role="alert" className="auth-error">{error}</p>}
    {expense.status === 'UNPAID' && <form onSubmit={e => { e.preventDefault(); if (!date || date > localToday()) { setError('Bitte wähle ein gültiges Zahlungsdatum.'); return } void run(() => api.payExpense(expense.businessId, expense.id, date, method)) }}><div className="expense-grid"><FormField label="Zahlungsdatum"><Input type="date" required max={localToday()} value={date} disabled={pending} onChange={e => setDate(e.target.value)} /></FormField><FormField label="Zahlungsmethode"><Select value={method} disabled={pending} onChange={e => setMethod(e.target.value as keyof typeof api.paymentMethods)}>{Object.entries(api.paymentMethods).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</Select></FormField></div><Button type="submit" disabled={pending}>Als bezahlt markieren · {formatPrice(expense.grossAmount, expense.currency)}</Button></form>}
    <ul className="receipt-list">{expense.payments?.map(payment => <li key={payment.id}><div><strong>{payment.paymentDate} · {formatPrice(payment.amount, payment.currency)}</strong><span>{api.paymentMethods[payment.method]} · {payment.reversedAt ? `Korrigiert: ${payment.reversalReason}` : 'Aktive Zahlung'}</span><small>Erfasst: {new Date(payment.recordedAt).toLocaleString('de-CH')}</small></div>{!payment.reversedAt && <Button disabled={pending} variant="secondary" onClick={() => { if (!reason.trim()) { setError('Bitte gib eine Begründung für die Korrektur ein.'); return } void run(() => api.reversePayment(expense.businessId, expense.id, payment.id, reason.trim())) }}>Zahlungsmarkierung zurücknehmen</Button>}</li>)}</ul>
    {expense.status !== 'CANCELLED' && <FormField label="Begründung für Korrektur oder Storno"><Input maxLength={500} value={reason} disabled={pending} onChange={e => setReason(e.target.value)} /></FormField>}
    {expense.status === 'UNPAID' && <Button variant="tertiary" disabled={pending} onClick={() => { if (!reason.trim()) { setError('Bitte gib eine Stornobegründung ein.'); return } if (window.confirm('Diese Ausgabe endgültig stornieren?')) void run(() => api.cancelExpense(expense.businessId, expense.id, reason.trim())) }}>Ausgabe stornieren</Button>}
    {expense.cancellationReason && <p>Stornogrund: {expense.cancellationReason}</p>}
  </section>
}
