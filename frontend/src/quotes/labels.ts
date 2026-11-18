import type { QuoteAction, QuoteStatus } from '../api/quotes'

export const quoteStatusLabels: Record<QuoteStatus, string> = { DRAFT: 'Entwurf', SENT: 'Gesendet', ACCEPTED: 'Angenommen', REJECTED: 'Abgelehnt' }
export const quoteStatusTones: Record<QuoteStatus, 'neutral' | 'warning' | 'positive' | 'negative'> = { DRAFT: 'neutral', SENT: 'warning', ACCEPTED: 'positive', REJECTED: 'negative' }

/** Documented transitions: DRAFT → SENT → ACCEPTED | REJECTED. Only drafts are editable or deletable. */
export const quoteActions: Record<QuoteStatus, QuoteAction[]> = { DRAFT: ['send'], SENT: ['accept', 'reject'], ACCEPTED: [], REJECTED: [] }

export const actionCopy: Record<QuoteAction, { label: string; title: string; description: string; confirm: string; done: string }> = {
  send: { label: 'Als gesendet markieren', title: 'Angebot als gesendet markieren?', description: 'Danach kann das Angebot nicht mehr bearbeitet oder gelöscht werden. Kundendaten werden für das Dokument fixiert.', confirm: 'Als gesendet markieren', done: 'Angebot als gesendet markiert.' },
  accept: { label: 'Angenommen', title: 'Angebot als angenommen markieren?', description: 'Ein angenommenes Angebot ist abgeschlossen und bleibt unverändert erhalten.', confirm: 'Annahme bestätigen', done: 'Angebot als angenommen markiert.' },
  reject: { label: 'Abgelehnt', title: 'Angebot als abgelehnt markieren?', description: 'Ein abgelehntes Angebot ist abgeschlossen und bleibt unverändert erhalten.', confirm: 'Ablehnung bestätigen', done: 'Angebot als abgelehnt markiert.' },
}

export function isExpired(status: QuoteStatus, validUntil: string, today: string) {
  return status === 'SENT' && validUntil < today
}
