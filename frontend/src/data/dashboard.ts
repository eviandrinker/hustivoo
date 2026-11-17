// Demo fixtures only. These figures are not fetched from a business account.
export const demoDashboard = {
  period: 'November 2026',
  currency: 'CHF',
  revenue: 6240,
  expenses: 1480,
  hours: 68,
  goal: 8000,
  revenueHistory: [
    { month: 'Jun', value: 3200 }, { month: 'Jul', value: 4100 },
    { month: 'Aug', value: 3650 }, { month: 'Sep', value: 4800 },
    { month: 'Oct', value: 5520 }, { month: 'Nov', value: 6240 },
  ],
  projects: [
    { name: 'Brand & website refresh', client: 'Studio North', category: 'Design', progress: 75, due: 'Nov 24' },
    { name: 'Monthly content package', client: 'Bloom & Co.', category: 'Marketing', progress: 45, due: 'Nov 28' },
    { name: 'Booking system setup', client: 'Alpine Wellness', category: 'Development', progress: 30, due: 'Dec 04' },
  ],
  invoices: [
    { id: 'INV-026', client: 'Studio North', amount: 1800, due: 'Nov 12', status: 'Overdue' },
    { id: 'INV-027', client: 'Bloom & Co.', amount: 950, due: 'Nov 25', status: 'Pending' },
    { id: 'INV-028', client: 'Alpine Wellness', amount: 1250, due: 'Nov 30', status: 'Pending' },
  ],
}

export const formatMoney = (value: number) => new Intl.NumberFormat('en-CH', {
  style: 'currency', currency: demoDashboard.currency, maximumFractionDigits: 0,
}).format(value)
