import { useEffect, useId, useRef } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'
import { Button } from './button'

export function Dialog({ open, onClose, title, description, children, footer, alert = false, className = '', iconClose = false, closeDisabled = false, initialFocus, returnFocus }: {
  open: boolean; onClose: () => void; title: string; description?: string
  children?: ReactNode; footer?: ReactNode; alert?: boolean; className?: string; iconClose?: boolean; closeDisabled?: boolean; initialFocus?: string; returnFocus?: string
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const id = useId()
  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (!open) { dialog.close(); return }
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    dialog.showModal()
    if (initialFocus) dialog.querySelector<HTMLElement>(initialFocus)?.focus()
    return () => {
      dialog.close()
      if (previousFocus?.isConnected) previousFocus.focus()
      else if (returnFocus) document.querySelector<HTMLElement>(returnFocus)?.focus()
    }
  }, [open, initialFocus, returnFocus])
  return (
    <dialog ref={ref} className={`dialog ${className}`} role={alert ? 'alertdialog' : undefined} aria-labelledby={`${id}-title`} aria-describedby={description ? `${id}-description` : undefined} onCancel={event => { event.preventDefault(); if (!closeDisabled) onClose() }}>
      <div className="dialog-heading"><h2 id={`${id}-title`}>{title}</h2><Button variant="tertiary" className={iconClose ? 'dialog-close-icon' : undefined} disabled={closeDisabled} onClick={onClose} aria-label={iconClose ? 'Dialog schliessen' : 'Close dialog'}>{iconClose ? <X size={18} aria-hidden="true" /> : 'Close'}</Button></div>
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
