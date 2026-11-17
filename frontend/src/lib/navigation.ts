import { LayoutDashboard, Users, FolderKanban, Clock3, FileText, Wallet, Settings } from 'lucide-react'

export const navigation = [
  { path: '/dashboard', title: 'Dashboard', icon: LayoutDashboard, description: '' },
  { path: '/customers', title: 'Customers', icon: Users, description: 'A little more personal. Keep your client relationships and contact details together.' },
  { path: '/projects', title: 'Projects', icon: FolderKanban, description: 'From first brief to final delivery. Give every project a clear home.' },
  { path: '/time-tracking', title: 'Time Tracking', icon: Clock3, description: 'Make every hour count. See where your time goes across your side business.' },
  { path: '/invoices', title: 'Invoices', icon: FileText, description: 'Bring a little order to getting paid. Keep your invoices and payment status together.' },
  { path: '/expenses', title: 'Expenses', icon: Wallet, description: 'Know what goes out. Keep your business costs organized in one place.' },
  { path: '/settings', title: 'Settings', icon: Settings, description: 'Make Hustivoo your own. Your workspace preferences will live here.' },
]
