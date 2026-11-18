import { useId, useRef, useState } from 'react'
import { Archive, ArchiveRestore, MoreHorizontal, Pencil } from 'lucide-react'
import type { CatalogService } from '@/api/services'
import { Button } from '@/components/ui/button'

/** Native popover provides light-dismiss/Escape without clipping in the scrollable table. */
export function ServiceActions({ service, pending, onEdit, onArchive }: {
  service: CatalogService; pending: boolean
  onEdit: (service: CatalogService) => void; onArchive: (service: CatalogService) => void
}) {
  const id = useId()
  const trigger = useRef<HTMLButtonElement>(null)
  const popover = useRef<HTMLDivElement>(null)
  const [position, setPosition] = useState({ top: 0, left: 0 })
  function act(callback: (service: CatalogService) => void) {
    popover.current?.hidePopover()
    trigger.current?.focus()
    callback(service)
  }
  return <>
    <Button ref={trigger} variant="tertiary" className="service-actions-trigger" disabled={pending}
      aria-label={`Aktionen für ${service.name}`} popoverTarget={id} onClick={() => {
        const rect = trigger.current?.getBoundingClientRect()
        if (rect) setPosition({ top: Math.max(8, Math.min(rect.bottom + 6, window.innerHeight - 112)), left: Math.max(8, Math.min(rect.right - 192, window.innerWidth - 200)) })
      }}><MoreHorizontal size={18} aria-hidden="true" /></Button>
    <div ref={popover} id={id} popover="auto" className="service-actions-popover" style={position} role="group" aria-label={`Aktionen für ${service.name}`}>
      <Button variant="tertiary" disabled={pending} aria-label={`${service.name} bearbeiten`} onClick={() => act(onEdit)}><Pencil size={15} aria-hidden="true" />Bearbeiten</Button>
      <Button variant="tertiary" disabled={pending} aria-label={`${service.name} ${service.active ? 'archivieren' : 'wiederherstellen'}`} onClick={() => act(onArchive)}>{service.active ? <Archive size={15} aria-hidden="true" /> : <ArchiveRestore size={15} aria-hidden="true" />}{service.active ? 'Archivieren' : 'Wiederherstellen'}</Button>
    </div>
  </>
}
