import type { ComponentProps, ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function PageHeader({ title, description, meta }: { title: string; description: string; meta?: ReactNode }) {
  return <header className="page-header"><div><h1>{title}</h1><p>{description}</p></div>{meta && <div className="page-meta">{meta}</div>}</header>
}
export function SectionHeader({ title, description, action, id }: { title: string; description?: string; action?: ReactNode; id?: string }) {
  return <div className="section-header"><div><h2 id={id}>{title}</h2>{description && <p>{description}</p>}</div>{action}</div>
}
export function StatusIndicator({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'positive' | 'warning' | 'negative' }) {
  return <span className={cn('status-indicator', `status--${tone}`)}><span aria-hidden="true" className="status-dot" />{children}</span>
}
export function InformationPanel({ children, className, ...props }: ComponentProps<'aside'>) {
  return <aside className={cn('information-panel', className)} {...props}>{children}</aside>
}
export function EmptyState({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <div className="empty-state"><h2>{title}</h2><p>{description}</p>{action}</div>
}
export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return <div className="feedback-state" role="status"><span className="loading-mark" aria-hidden="true" />{label}</div>
}
export function ErrorState({ title = 'Unable to load this information', description, action }: { title?: string; description: string; action?: ReactNode }) {
  return <div className="feedback-state error-state" role="alert"><strong>{title}</strong><p>{description}</p>{action}</div>
}
