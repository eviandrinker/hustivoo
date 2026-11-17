import { cloneElement, useId } from 'react'
import type { ComponentProps, ReactElement } from 'react'
import { cn } from '@/lib/utils'

export function Input({ className, ...props }: ComponentProps<'input'>) {
  return <input className={cn('form-control', className)} {...props} />
}
export function Textarea({ className, ...props }: ComponentProps<'textarea'>) {
  return <textarea className={cn('form-control', className)} {...props} />
}
export function Select({ className, ...props }: ComponentProps<'select'>) {
  return <select className={cn('form-control', className)} {...props} />
}
export function Checkbox({ className, ...props }: Omit<ComponentProps<'input'>, 'type'>) {
  return <input type="checkbox" className={cn('checkbox', className)} {...props} />
}

type FieldControl = { id?: string; 'aria-describedby'?: string; 'aria-invalid'?: boolean | 'true' | 'false' }
export function FormField({ label, hint, error, children }: {
  label: string; hint?: string; error?: string; children: ReactElement<FieldControl>
}) {
  const generatedId = useId()
  const id = children.props.id ?? generatedId
  const description = [children.props['aria-describedby'], hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ') || undefined
  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      {cloneElement(children, { id, 'aria-describedby': description, 'aria-invalid': error ? true : children.props['aria-invalid'] })}
      {hint && <p id={`${id}-hint`} className="field-hint">{hint}</p>}
      {error && <p id={`${id}-error`} className="field-error" role="alert">{error}</p>}
    </div>
  )
}
