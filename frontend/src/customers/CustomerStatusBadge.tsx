import type { CustomerStatus } from '@/api/customers'
import { customerStatusLabels } from './labels'
export function CustomerStatusBadge({ status }: { status: CustomerStatus }) {
  return <span className={`crm-status crm-status--${status.toLowerCase()}`}>{customerStatusLabels[status]}</span>
}
