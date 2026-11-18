import { useRef, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { Menu, X } from 'lucide-react'
import { navigation } from '@/lib/navigation'
import { useBusiness } from '@/business/context'
import { Select } from '@/components/ui/forms'
import { cn } from '@/lib/utils'

export function Sidebar() {
  const business = useBusiness()
  const [open, setOpen] = useState(false)
  const trigger = useRef<HTMLButtonElement>(null)
  function close() {
    setOpen(false)
    trigger.current?.focus()
  }
  return (
    <aside className="sidebar" onKeyDown={event => { if (event.key === 'Escape' && open) close() }}>
      <NavLink to="/dashboard" className="brand" onClick={() => setOpen(false)} aria-label="Hustivoo dashboard"><span className="brand-symbol" aria-hidden="true">h</span>Hustivoo</NavLink>
      <button ref={trigger} type="button" className="mobile-menu" aria-expanded={open} aria-controls="main-navigation" aria-label={open ? 'Close navigation' : 'Open navigation'} onClick={() => setOpen(!open)}>{open ? <X size={20} /> : <Menu size={20} />}</button>
      <div id="main-navigation" className={cn('sidebar-content', open && 'is-open')}>
        <div className="business-switcher">
          {business.businesses.length > 1 ? <><label htmlFor="active-business">Dein Unternehmen</label><Select id="active-business" value={business.active?.id ?? ''} onChange={event => business.select(event.target.value)}>{business.businesses.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</Select></> : <p>{business.active?.name}</p>}
        </div>
        <nav aria-label="Main navigation">
          {navigation.map(({ path, title, icon: Icon }) => <NavLink key={path} to={path} onClick={() => setOpen(false)} className={({ isActive }) => cn('nav-item', isActive && 'active')}><Icon size={18} aria-hidden="true" />{title}</NavLink>)}
        </nav>
        <div className="sidebar-bottom"><p className="workspace-note">Your side business.<br />One place.</p><p className="developer-credit">Made by <strong>Margraf Digital</strong></p></div>
      </div>
    </aside>
  )
}
