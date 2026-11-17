import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { Button } from './button'

export function Dialog({ open, onClose, title, description, children, footer, alert = false }: {
  open: boolean; onClose: () => void; title: string; description?: string
  children?: ReactNode; footer?: ReactNode; alert?: boolean
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const id = useId()
  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (!open) { dialog.close(); return }
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    dialog.showModal()
    return () => { dialog.close(); previousFocus?.focus() }
  }, [open])
  return (
    <dialog ref={ref} className="dialog" role={alert ? 'alertdialog' : undefined} aria-labelledby={`${id}-title`} aria-describedby={description ? `${id}-description` : undefined} onCancel={event => { event.preventDefault(); onClose() }}>
      <div className="dialog-heading"><h2 id={`${id}-title`}>{title}</h2><Button variant="tertiary" onClick={onClose} aria-label="Close dialog">Close</Button></div>
      {description && <p id={`${id}-description`}>{description}</p>}
      {children && <div className="dialog-body">{children}</div>}
      {footer && <div className="dialog-footer">{footer}</div>}
    </dialog>
  )
}

export function ConfirmationDialog({ open, onClose, onConfirm, title, description, confirmLabel = 'Confirm', destructive = false }: {
  open: boolean; onClose: () => void; onConfirm: () => void; title: string
  description: string; confirmLabel?: string; destructive?: boolean
}) {
  return <Dialog open={open} onClose={onClose} title={title} description={description} alert footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button variant={destructive ? 'destructive' : 'primary'} onClick={onConfirm}>{confirmLabel}</Button></>} />
}
