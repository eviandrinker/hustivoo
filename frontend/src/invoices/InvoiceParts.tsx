import type { DisplayStatus } from '@/api/invoices'
import { StatusIndicator } from '@/components/ui/display'
import { invoiceStatusLabels, invoiceStatusTones } from './labels'

export function InvoiceStatusBadge({ status }: { status: DisplayStatus }) {
  return <span className="quote-status"><StatusIndicator tone={invoiceStatusTones[status]}>{invoiceStatusLabels[status]}</StatusIndicator></span>
}
