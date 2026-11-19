import { LayoutDashboard, Users, FolderKanban, Clock3, FileText, Wallet, Settings, BriefcaseBusiness, FileSignature } from 'lucide-react'

export const navigation = [
  { path: '/dashboard', title: 'Dashboard', icon: LayoutDashboard, description: '' },
  { path: '/customers', title: 'Customers', icon: Users, description: 'A little more personal. Keep your client relationships and contact details together.' },
  { path: '/services', title: 'Leistungen', icon: BriefcaseBusiness, description: 'Verwalte deine Dienstleistungen und Preise an einem Ort.' },
  { path: '/quotes', title: 'Angebote', icon: FileSignature, description: 'Erstelle Angebote und verfolge ihren Status.' },
  { path: '/projects', title: 'Projects', icon: FolderKanban, description: 'From first brief to final delivery. Give every project a clear home.' },
  { path: '/time-tracking', title: 'Time Tracking', icon: Clock3, description: 'Make every hour count. See where your time goes across your side business.' },
  { path: '/invoices', title: 'Rechnungen', icon: FileText, description: 'Rechnungen mit QR-Zahlteil und offene Beträge.' },
  { path: '/expenses', title: 'Ausgaben', icon: Wallet, description: 'Know what goes out. Keep your business costs organized in one place.' },
  { path: '/settings', title: 'Einstellungen', icon: Settings, description: 'Stammdaten und Branding deines Unternehmens.' },
]
