import { useCallback, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { useBusiness } from '@/business/context'
import * as api from '@/api/expenses'
import { useExpenseResource } from '@/expenses/useExpenseResource'
import { ExpenseForm } from '@/expenses/ExpenseForm'
import { PaymentPanel } from '@/expenses/PaymentPanel'
import { Receipts } from '@/expenses/Receipts'
import { statusLabels } from '@/expenses/validation'
import { Button } from '@/components/ui/button'
import { PageHeader, ErrorState, LoadingState, StatusIndicator } from '@/components/ui/display'
export function ExpenseEditorPage() {
  const { active } = useBusiness(); const { expenseId } = useParams(); const [origin] = useState(active?.id)
  if (!active) return null
  if (origin !== active.id) return <Navigate to="/expenses" replace />
  return <Editor key={`${active.id}:${expenseId ?? 'new'}`} business={active.id} id={expenseId} />
}
function Editor({ business, id }: { business: string; id?: string }) {
  const navigate = useNavigate(); const [message, setMessage] = useState(''); const [error, setError] = useState('')
  const resource = useExpenseResource(useCallback(async () => {
    const [expense, suppliers, archived, categories] = await Promise.all([id ? api.getExpense(business, id) : Promise.resolve(null), api.listSuppliers(business), api.listSuppliers(business, '', true), api.listCategories(business)])
    return { expense, suppliers: [...suppliers, ...archived], categories }
  }, [business, id]))
  const expense = resource.data?.expense
  return <div className="page-content expense-page"><Link className="text-link" to="/expenses">← Alle Ausgaben</Link>
    <PageHeader title={id ? expense?.description ?? 'Ausgabe' : 'Neue Ausgabe'} description="Beträge, Zahlung und Belege nachvollziehbar erfassen." meta={<Link className="button button--secondary" to="/expenses/settings">Lieferanten & Kategorien</Link>} />
    {message && <p role="status" className="auth-success">{message}</p>}{error && <p role="alert" className="auth-error">{error}</p>}
    {resource.status === 'loading' && <LoadingState label="Ausgabendaten werden geladen…" />}
    {resource.status === 'error' && <ErrorState description="Ausgabe oder Stammdaten nicht verfügbar." action={<Button onClick={() => void resource.store.load()}>Erneut laden</Button>} />}
    {resource.data && <>
      {expense && <StatusIndicator tone={expense.status === 'PAID' ? 'positive' : 'neutral'}>{statusLabels[expense.displayStatus]}</StatusIndicator>}
      {!resource.data.suppliers.length && <p className="information-panel">Erstelle zuerst einen Lieferanten unter „Lieferanten & Kategorien“.</p>}
      {!resource.data.categories.length && <p className="information-panel">Lege eine Kategorie an oder übernimm dort die vorgeschlagenen Kategorien.</p>}
      {expense && <p className="field-hint">Lieferantensnapshot: {expense.supplier.name} · {expense.categoryName}. Änderungen der Stammdaten ändern diesen Beleg nicht.</p>}
      <ExpenseForm key={expense?.updatedAt ?? 'new'} expense={expense ?? undefined} suppliers={resource.data.suppliers} categories={resource.data.categories} save={async input => {
        const saved = await resource.store.run(() => api.saveExpense(business, input, id)); if (!saved) return
        if (!id) navigate(`/expenses/${saved.id}`, { replace: true })
        else { setMessage('Ausgabe gespeichert.'); await resource.store.load() }
      }} />
      {expense && <><PaymentPanel expense={expense} apply={async operation => { const result = await resource.store.run(operation); if (result) { setMessage('Änderung gespeichert.'); await resource.store.load() } }} />
        <Receipts business={business} expense={expense} reload={() => resource.store.load()} />
        {expense.status === 'UNPAID' && !expense.payments?.length && !expense.receipts?.length && <Button variant="tertiary" onClick={async () => { if (!window.confirm('Diese unbezahlte Ausgabe ohne Belege endgültig löschen?')) return; try { const removed = await resource.store.run(async () => { await api.deleteExpense(business, expense.id); return true }); if (removed) navigate('/expenses', { replace: true }) } catch { setError('Ausgabe konnte nicht gelöscht werden.') } }}>Ausgabe löschen</Button>}
      </>}
    </>}
  </div>
}
