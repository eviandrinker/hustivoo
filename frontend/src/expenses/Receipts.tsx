import { useEffect, useRef, useState } from 'react'
import * as api from '@/api/expenses'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { validateReceipt } from './validation'
export function Receipts({ business, expense, reload }: { business: string; expense: api.Expense; reload: () => Promise<void> }) {
  const [progress, setProgress] = useState<number | null>(null); const [error, setError] = useState(''); const [preview, setPreview] = useState<{ url: string; receipt: api.Receipt } | null>(null)
  const controller = useRef<AbortController | null>(null); const busy = useRef(false); const mounted = useRef(true); const fileInput = useRef<HTMLInputElement>(null)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; controller.current?.abort() } }, [])
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview.url) }, [preview])
  async function upload(files: File[]) {
    if (busy.current || expense.status === 'CANCELLED') return
    const invalid = files.map(validateReceipt).find(Boolean); if (invalid) { setError(invalid); return }
    busy.current = true; setError(''); setProgress(0); controller.current = new AbortController()
    try { for (const file of files) { await api.uploadReceipt(business, expense.id, file, n => { if (mounted.current) setProgress(n) }, controller.current.signal) } if (mounted.current) await reload() }
    catch { if (mounted.current) setError('Upload nicht bestätigt. Lade die Ausgabe vor einem erneuten Upload neu. Zulässig: PDF (max. 30 Seiten), JPEG und PNG bis 10 MB.') }
    finally { busy.current = false; if (mounted.current) setProgress(null) }
  }
  async function view(receipt: api.Receipt, download: boolean) {
    if (busy.current) return
    busy.current = true; setError(''); controller.current = new AbortController()
    try {
      const blob = await api.receiptBlob(business, expense.id, receipt, controller.current.signal)
      if (!mounted.current) return
      const url = URL.createObjectURL(blob)
      if (download) { const link = document.createElement('a'); link.href = url; link.download = receipt.filename; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
      else setPreview({ url, receipt })
    } catch { if (mounted.current) setError('Beleg konnte nicht geladen werden.') }
    finally { busy.current = false }
  }
  return <section className="surface expense-panel"><h2>Belege</h2>
    {error && <p role="alert" className="auth-error">{error}</p>}
    {expense.status !== 'CANCELLED' && <div className="receipt-dropzone" onDragOver={e => e.preventDefault()} onDrop={e => { e.preventDefault(); void upload(Array.from(e.dataTransfer.files)) }}>
      <p>PDF, JPEG oder PNG hier ablegen</p><p className="field-hint">Bis 10 MB pro Datei. Dateien werden sicher aufbereitet; PDFs verlieren interaktive Inhalte.</p>
      <input ref={fileInput} type="file" accept="application/pdf,image/png,image/jpeg" multiple aria-label="Belege auswählen" disabled={progress !== null} onChange={e => { void upload(Array.from(e.target.files ?? [])); e.target.value = '' }} />
    </div>}
    {progress !== null && <div role="status"><progress max={100} value={progress} aria-label="Upload-Fortschritt" /><span>{progress === 100 ? 'Wird geprüft und gespeichert…' : `${progress} % hochgeladen`}</span></div>}
    {!expense.receipts?.length && <p className="field-hint">Noch keine Belege hinterlegt.</p>}
    <ul className="receipt-list">{expense.receipts?.map(receipt => <li key={receipt.id}><div><strong>{receipt.filename}</strong><span>{(receipt.sizeBytes / 1024).toFixed(0)} KB</span></div><div className="page-actions"><Button variant="tertiary" onClick={() => void view(receipt, false)}>Anzeigen</Button><Button variant="tertiary" onClick={() => void view(receipt, true)}>Download</Button>{expense.status === 'UNPAID' && !expense.payments?.length && <Button variant="tertiary" disabled={progress !== null} onClick={async () => {
      if (busy.current || !window.confirm('Diesen Beleg dauerhaft entfernen?')) return
      busy.current = true; setError('')
      try { await api.removeReceipt(business, expense.id, receipt.id); if (mounted.current) await reload() } catch { if (mounted.current) setError('Beleg konnte nicht entfernt werden.') } finally { busy.current = false }
    }}>Entfernen</Button>}</div></li>)}</ul>
    {preview && <Dialog open title={preview.receipt.filename} onClose={() => setPreview(null)}>{preview.receipt.contentType === 'application/pdf' ? <iframe className="receipt-preview" title="PDF-Belegvorschau" sandbox="" src={preview.url} /> : <img className="receipt-image" src={preview.url} alt={`Beleg: ${preview.receipt.filename}`} />}<p className="field-hint">Falls die PDF-Vorschau im Browser nicht verfügbar ist, nutze den Download.</p></Dialog>}
  </section>
}
