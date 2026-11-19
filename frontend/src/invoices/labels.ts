import type { DisplayStatus, Invoice, PaymentMethod } from '../api/invoices'

export const invoiceStatusLabels: Record<DisplayStatus, string> = {
  DRAFT: 'Entwurf', ISSUED: 'Offen', PARTIALLY_PAID: 'Teilweise bezahlt', PAID: 'Bezahlt', OVERDUE: 'Überfällig', CANCELLED: 'Storniert',
}
export const invoiceStatusTones: Record<DisplayStatus, 'neutral' | 'warning' | 'positive' | 'negative'> = {
  DRAFT: 'neutral', ISSUED: 'warning', PARTIALLY_PAID: 'warning', PAID: 'positive', OVERDUE: 'negative', CANCELLED: 'neutral',
}
export const paymentMethodLabels: Record<PaymentMethod, string> = {
  BANK_TRANSFER: 'Banküberweisung', QR_BILL: 'QR-Rechnung', CASH: 'Bar', CARD: 'Karte', TWINT: 'TWINT', OTHER: 'Andere',
}

/** Documented rules: drafts are edited/issued/deleted; open invoices take payments; only unpaid issued ones can be cancelled. */
export function invoiceCapabilities(invoice: Pick<Invoice, 'status' | 'items' | 'grandTotal'>) {
  const open = invoice.status === 'ISSUED' || invoice.status === 'PARTIALLY_PAID'
  return {
    edit: invoice.status === 'DRAFT',
    remove: invoice.status === 'DRAFT',
    issue: invoice.status === 'DRAFT',
    pay: open,
    cancel: invoice.status === 'ISSUED',
    reverse: invoice.status !== 'DRAFT' && invoice.status !== 'CANCELLED',
  }
}

export function issueBlocker(invoice: Pick<Invoice, 'items' | 'grandTotal'>): string | null {
  if (!invoice.items.length) return 'Füge mindestens eine Position hinzu, bevor du die Rechnung ausstellst.'
  if (!/[1-9]/.test(invoice.grandTotal)) return 'Eine Rechnung mit Total 0.00 kann nicht ausgestellt werden.'
  return null
}

export function invoicePdfFilename(invoiceNumber: string) {
  return `Rechnung-${invoiceNumber.replace(/[^A-Za-z0-9-]/g, '')}.pdf`
}
