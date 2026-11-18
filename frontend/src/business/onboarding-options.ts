export const industries = ['Design & Kreatives', 'IT & Software', 'Beratung', 'Marketing & Kommunikation', 'Bildung & Coaching', 'Handwerk', 'Gesundheit & Wellness', 'Fotografie & Video', 'Handel']
export const legalForms = ['Einzelfirma', 'GmbH', 'AG', 'Kollektivgesellschaft', 'Verein', 'Genossenschaft']
export const countries = [
  ['CH', 'Schweiz'], ['DE', 'Deutschland'], ['AT', 'Österreich'], ['LI', 'Liechtenstein'],
  ['FR', 'Frankreich'], ['IT', 'Italien'], ['NL', 'Niederlande'], ['BE', 'Belgien'],
  ['LU', 'Luxemburg'], ['ES', 'Spanien'], ['PT', 'Portugal'], ['GB', 'Vereinigtes Königreich'],
  ['US', 'Vereinigte Staaten'], ['CA', 'Kanada'], ['AU', 'Australien'],
] as const
export const currencies = [
  ['CHF', 'CHF – Schweizer Franken'], ['EUR', 'EUR – Euro'], ['USD', 'USD – US-Dollar'],
  ['GBP', 'GBP – Britisches Pfund'], ['CAD', 'CAD – Kanadischer Dollar'], ['AUD', 'AUD – Australischer Dollar'],
] as const
export function optionalSelection(selection: string, custom: string): string | null {
  return (selection === 'other' ? custom.trim() || 'Sonstiges' : selection) || null
}
