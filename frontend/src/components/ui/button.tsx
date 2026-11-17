import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

type ButtonProps = ComponentProps<'button'> & {
  variant?: 'primary' | 'secondary' | 'tertiary' | 'destructive'
}

export function Button({ variant = 'primary', className, type = 'button', ...props }: ButtonProps) {
  return <button type={type} className={cn('button', `button--${variant}`, className)} {...props} />
}
