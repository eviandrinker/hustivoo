import type { ReactNode } from 'react'
import { EmptyState } from './display'

export interface TableColumn<T> {
  key: string
  header: string
  numeric?: boolean
  render: (row: T) => ReactNode
}
export function DataTable<T>({ caption, columns, rows, rowKey, onRowClick, emptyTitle = 'No records yet', emptyDescription = 'Records will appear here when available.' }: {
  caption: string; columns: TableColumn<T>[]; rows: T[]; rowKey: (row: T) => string
  onRowClick?: (row: T) => void
  emptyTitle?: string; emptyDescription?: string
}) {
  return (
    <div className="table-scroll" role="region" aria-label={caption} tabIndex={0}>
      <table className="data-table">
        <caption className="sr-only">{caption}</caption>
        <thead><tr>{columns.map(column => <th key={column.key} scope="col" className={column.numeric ? 'numeric' : undefined}>{column.header}</th>)}</tr></thead>
        <tbody>{rows.length ? rows.map(row => <tr key={rowKey(row)} className={onRowClick ? 'clickable-row' : undefined} onClick={event => {
          if (!(event.target as HTMLElement).closest('a, button, input, select, textarea')) onRowClick?.(row)
        }}>{columns.map(column => <td key={column.key} className={column.numeric ? 'numeric' : undefined}>{column.render(row)}</td>)}</tr>) : <tr><td colSpan={columns.length}><EmptyState title={emptyTitle} description={emptyDescription} /></td></tr>}</tbody>
      </table>
    </div>
  )
}
