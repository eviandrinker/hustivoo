import { useRef, useState } from 'react'
import type { Expense, ExpenseInput, Supplier, Category, InputTax } from '@/api/expenses'
import { ApiError } from '@/api/client'
import { FormField, Input, Select, Textarea } from '@/components/ui/forms'
import { Button } from '@/components/ui/button'
import { localToday } from '@/quotes/validation'
import { formatPrice } from '@/services/validation'
import { validateExpense, expensePreview, inputTaxLabels } from './validation'
export function ExpenseForm({ expense, suppliers, categories, save }: { expense?: Expense; suppliers: Supplier[]; categories: Category[]; save: (x: ExpenseInput) => Promise<void> }) {
  const [form, setForm] = useState<ExpenseInput>(expense ?? { supplierId: '', categoryId: '', description: '', reference: null, expenseDate: localToday(), dueDate: null, currency: 'CHF', netAmount: '', taxRate: null, taxAmount: null, inputTax: 'UNCHECKED', notes: null })
  const [errors, setErrors] = useState<Record<string, string>>({}); const [error, setError] = useState(''); const [pending, setPending] = useState(false)
  const busy = useRef(false); const ref = useRef<HTMLFormElement>(null)
  const locked = Boolean(expense && (expense.status !== 'UNPAID' || expense.payments?.length))
  function change(key: keyof ExpenseInput, value: string | null) { setForm(previous => ({ ...previous, [key]: value })); setErrors(previous => ({ ...previous, [key]: '' })) }
  const preview = expensePreview(form.netAmount.replace(',', '.'), form.taxRate?.replace(',', '.') ?? null, form.taxAmount?.replace(',', '.') ?? null)
  return <form ref={ref} className="expense-editor surface" noValidate aria-busy={pending} onSubmit={async event => {
    event.preventDefault(); if (busy.current || locked) return
    const input = { ...form, description: form.description.trim(), currency: form.currency.trim().toUpperCase(), netAmount: form.netAmount.trim().replace(',', '.'), taxRate: form.taxRate?.trim().replace(',', '.') || null, taxAmount: form.taxAmount?.trim().replace(',', '.') || null }
    const invalid = validateExpense(input); setErrors(invalid); setError('')
    if (Object.keys(invalid).length) { requestAnimationFrame(() => ref.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()); return }
    busy.current = true; setPending(true)
    try { await save(input) } catch (error) { if (error instanceof ApiError && error.status === 400 && Object.keys(error.fields).length) setErrors(error.fields); else setError('Speichern konnte nicht bestätigt werden. Bitte prüfe die Ausgabenliste vor einem erneuten Versuch.') }
    finally { busy.current = false; setPending(false) }
  }}>
    {error && <p className="auth-error" role="alert">{error}</p>}
    {locked && <p className="information-panel">Ausgaben mit Zahlungshistorie oder Storno bleiben unverändert. Eine falsche Zahlung kannst du unten mit Begründung zurücknehmen.</p>}
    <fieldset disabled={pending || locked}><legend>Allgemeine Angaben</legend>
      <FormField label="Beschreibung" error={errors.description}><Input name="description" value={form.description} required onChange={e => change('description', e.target.value)} /></FormField>
      <div className="expense-grid"><FormField label="Lieferant" error={errors.supplierId}><Select name="supplierId" value={form.supplierId} onChange={e => change('supplierId', e.target.value)} required><option value="">Lieferant wählen</option>{suppliers.filter(x => !x.archived || x.id === form.supplierId).map(x => <option key={x.id} value={x.id}>{x.name}{x.archived ? ' (archiviert)' : ''}</option>)}</Select></FormField>
        <FormField label="Kategorie" error={errors.categoryId}><Select name="categoryId" value={form.categoryId} onChange={e => change('categoryId', e.target.value)} required><option value="">Kategorie wählen</option>{categories.filter(x => !x.archived || x.id === form.categoryId).map(x => <option key={x.id} value={x.id}>{x.name}{x.archived ? ' (archiviert)' : ''}</option>)}</Select></FormField>
        <FormField label="Ausgabedatum" error={errors.expenseDate}><Input type="date" name="expenseDate" value={form.expenseDate} required onChange={e => change('expenseDate', e.target.value)} /></FormField>
        <FormField label="Externe Belegnummer (optional)" error={errors.reference}><Input name="reference" value={form.reference ?? ''} onChange={e => change('reference', e.target.value || null)} /></FormField></div>
    </fieldset>
    <fieldset disabled={pending || locked}><legend>Beträge</legend><div className="expense-grid">
      <FormField label="Nettobetrag" error={errors.netAmount}><Input name="netAmount" inputMode="decimal" value={form.netAmount} required onChange={e => change('netAmount', e.target.value)} /></FormField>
      <FormField label="Währung" error={errors.currency}><Input name="currency" maxLength={3} value={form.currency} required onChange={e => change('currency', e.target.value)} /></FormField>
      <FormField label="MWST-Satz in %" hint="Leer lassen, wenn keine MWST ausgewiesen ist." error={errors.taxRate}><Input list="expense-tax-rates" name="taxRate" inputMode="decimal" value={form.taxRate ?? ''} placeholder="Kein MWST-Ausweis" onChange={e => change('taxRate', e.target.value || null)} /></FormField>
      <FormField label="Ausgewiesene MWST (optional)" hint="Leer: berechnet. Belegabweichung bis 0.05 möglich." error={errors.taxAmount}><Input name="taxAmount" inputMode="decimal" value={form.taxAmount ?? ''} placeholder={preview?.tax ?? 'Automatisch'} onChange={e => change('taxAmount', e.target.value || null)} /></FormField>
    </div><datalist id="expense-tax-rates"><option value="8.1">Normalsatz</option><option value="2.6">Reduziert</option><option value="3.8">Beherbergung</option><option value="0">0 %</option></datalist>
    <p className="expense-total" role="status">Bruttobetrag {preview ? formatPrice(preview.gross, form.currency) : '—'} <small>Vorschau · beim Speichern serverseitig geprüft</small></p></fieldset>
    <fieldset disabled={pending || locked}><legend>Zahlung & weitere Angaben</legend>
      <FormField label="Fälligkeit (optional)" error={errors.dueDate}><Input type="date" name="dueDate" value={form.dueDate ?? ''} onChange={e => change('dueDate', e.target.value || null)} /></FormField>
      <FormField label="Vorsteuerbehandlung" hint="Erfasste MWST bedeutet keinen automatischen Vorsteuerabzug." error={errors.inputTax}><Select name="inputTax" value={form.inputTax} onChange={e => change('inputTax', e.target.value as InputTax)}>{Object.entries(inputTaxLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</Select></FormField>
      <FormField label="Interne Notizen (optional)" error={errors.notes}><Textarea name="notes" rows={3} value={form.notes ?? ''} onChange={e => change('notes', e.target.value || null)} /></FormField>
    </fieldset>
    {!locked && <div className="page-actions"><Button type="submit" disabled={pending}>{pending ? 'Wird gespeichert…' : expense ? 'Änderungen speichern' : 'Ausgabe erstellen'}</Button></div>}
    {!expense && <p className="field-hint">Die Ausgabe startet als offen. Nach dem Speichern kannst du Zahlungen erfassen und mehrere Belege hochladen.</p>}
  </form>
}
