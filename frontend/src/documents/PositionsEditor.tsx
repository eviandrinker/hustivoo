import { useId, useState } from 'react'
import { ArrowDown, ArrowUp, Plus, Trash2 } from 'lucide-react'
import type { CatalogService } from '@/api/services'
import { Button } from '@/components/ui/button'
import { FormField, Input, Select, Textarea } from '@/components/ui/forms'
import { unitLabels } from '@/services/validation'
import { formatMoney, type LineAmounts } from '@/quotes/money'
import { freeItem, itemFromService, MAX_ITEMS, moveItem, type DraftItem } from '@/quotes/validation'

/**
 * Positions of a priced document (quote or invoice): catalogue snapshots and free positions with
 * live line amounts. Prices are copied from the catalogue once; later catalogue changes do not apply.
 */
export function PositionsEditor({ items, currency, services, pending, errors, lines, addFreeId, onItems, onItem }: {
  items: DraftItem[]
  currency: string
  services: CatalogService[]
  pending: boolean
  errors: Record<string, string>
  lines: (LineAmounts | null)[]
  addFreeId: string
  onItems: (items: DraftItem[], focus?: string) => void
  onItem: (index: number, key: keyof Omit<DraftItem, 'key' | 'serviceId'>, value: string) => void
}) {
  const [catalogChoice, setCatalogChoice] = useState('')
  const ids = { items: useId(), catalog: useId() }
  const itemError = (index: number, key: string) => errors[`items[${index}].${key}`]
  function addFromCatalog() {
    const service = services.find(item => item.id === catalogChoice)
    if (!service || items.length >= MAX_ITEMS) return
    onItems([...items, itemFromService(service)], `[data-item="${items.length}"] textarea`)
    setCatalogChoice('')
  }
  return <section className="quote-card" aria-labelledby={ids.items}>
    <div className="quote-card-heading"><h2 id={ids.items}>Positionen</h2><span>{items.length} {items.length === 1 ? 'Position' : 'Positionen'}</span></div>
    {errors.items && <p className="field-error" role="alert">{errors.items}</p>}
    {items.length ? <ol className="quote-items">{items.map((item, index) => {
      const line = lines[index]
      return <li key={item.key} data-item={index}>
        <fieldset className="quote-item" disabled={pending}>
          <legend className="sr-only">Position {index + 1}</legend>
          <div className="quote-item-head">
            <span className="quote-item-index" aria-hidden="true">{index + 1}</span>
            <span className="quote-item-source">{item.serviceId ? 'Aus Leistungskatalog' : 'Freie Position'}</span>
            <div className="quote-item-tools">
              <Button variant="tertiary" aria-label={`Position ${index + 1} nach oben`} disabled={index === 0} onClick={() => onItems(moveItem(items, index, -1))}><ArrowUp size={16} aria-hidden="true" /></Button>
              <Button variant="tertiary" aria-label={`Position ${index + 1} nach unten`} disabled={index === items.length - 1} onClick={() => onItems(moveItem(items, index, 1))}><ArrowDown size={16} aria-hidden="true" /></Button>
              <Button variant="tertiary" className="quote-item-remove" aria-label={`Position ${index + 1} entfernen`} onClick={() => onItems(items.filter((_, position) => position !== index), `#${addFreeId}`)}><Trash2 size={16} aria-hidden="true" /></Button>
            </div>
          </div>
          <FormField label="Beschreibung" error={itemError(index, 'description')}><Textarea name={`items[${index}].description`} rows={2} value={item.description} placeholder="Was wird geleistet?" onChange={event => onItem(index, 'description', event.target.value)} /></FormField>
          <div className="quote-item-grid">
            <FormField label="Menge" error={itemError(index, 'quantity')}><Input name={`items[${index}].quantity`} inputMode="decimal" value={item.quantity} onChange={event => onItem(index, 'quantity', event.target.value)} /></FormField>
            <FormField label="Einheit" error={itemError(index, 'unit')}><Select name={`items[${index}].unit`} value={item.unit} onChange={event => onItem(index, 'unit', event.target.value)}>{Object.entries(unitLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select></FormField>
            <FormField label={`Einzelpreis (${currency})`} error={itemError(index, 'unitPrice')}><Input name={`items[${index}].unitPrice`} inputMode="decimal" placeholder="0.00" value={item.unitPrice} onChange={event => onItem(index, 'unitPrice', event.target.value)} /></FormField>
            <FormField label="MWST %" error={itemError(index, 'taxRate')}><Input name={`items[${index}].taxRate`} inputMode="decimal" value={item.taxRate} onChange={event => onItem(index, 'taxRate', event.target.value)} /></FormField>
            <div className="quote-item-amount" aria-live="polite"><span>Betrag netto</span><strong>{line ? formatMoney(line.subtotal, currency) : '—'}</strong></div>
          </div>
          {itemError(index, 'serviceId') && <p className="field-error" role="alert">Diese Leistung ist nicht mehr verfügbar. Entferne die Position oder wähle eine andere Leistung.</p>}
        </fieldset>
      </li>
    })}</ol> : <p className="quote-empty-items">Noch keine Positionen. Füge Leistungen aus deinem Katalog oder freie Positionen hinzu.</p>}
    <div className="quote-add">
      <div className="quote-add-catalog">
        <label htmlFor={ids.catalog}>Leistung aus Katalog</label>
        <div>
          <Select id={ids.catalog} name="catalogService" value={catalogChoice} disabled={pending || !services.length} onChange={event => setCatalogChoice(event.target.value)}>
            <option value="">{services.length ? 'Leistung auswählen…' : 'Keine aktiven Leistungen'}</option>
            {services.map(service => <option key={service.id} value={service.id} disabled={service.currency !== currency}>
              {service.name} · {formatMoney(service.unitPrice, service.currency)} / {unitLabels[service.unit]}{service.currency !== currency ? ` (nur ${service.currency})` : ''}
            </option>)}
          </Select>
          <Button variant="secondary" disabled={pending || !catalogChoice || items.length >= MAX_ITEMS} onClick={addFromCatalog}><Plus size={16} aria-hidden="true" />Hinzufügen</Button>
        </div>
      </div>
      <Button id={addFreeId} variant="tertiary" disabled={pending || items.length >= MAX_ITEMS} onClick={() => onItems([...items, freeItem(items.at(-1))], `[data-item="${items.length}"] textarea`)}><Plus size={16} aria-hidden="true" />Freie Position</Button>
    </div>
  </section>
}
