export const B = 'business-a'
export const item = { id: 'item-1', serviceId: 'service-a', position: 1, description: 'Beratung\nWorkshop', quantity: '1.5', unit: 'HOUR', unitPrice: '95.10', taxRate: '8.10', lineSubtotal: '142.65', lineTax: '11.55', lineTotal: '154.20' }
export const summary = { id: 'invoice-a', businessId: B, customerId: 'customer-a', invoiceNumber: 'RE-0001', status: 'ISSUED', displayStatus: 'ISSUED', overdue: false,
  customerName: 'Muster AG', issueDate: '2026-11-18', dueDate: '2026-12-18', currency: 'CHF', grandTotal: '154.20', paidTotal: '0.00', openAmount: '154.20', updatedAt: '2026-11-18T10:00:00Z' }
export const invoice = {
  ...summary, paymentTermDays: 30, introductionText: 'Vielen Dank', closingText: 'Freundliche Grüsse', notes: 'Mahnung intern', subtotal: '142.65', taxTotal: '11.55',
  customer: { customerNumber: 'K-0001', name: 'Muster AG', companyName: 'Muster AG', contactName: 'Erika Muster', street: 'Bahnhofstrasse 1', postalCode: '3000', city: 'Bern', country: 'CH', email: null },
  items: [item], taxGroups: [{ rate: '8.10', base: '142.65', tax: '11.55' }], payments: [], quote: { id: 'quote-a', quoteNumber: 'ANG-0007' },
  qrBill: { available: true, problems: [] }, issuedAt: '2026-11-18T11:00:00Z', cancelledAt: null, cancellationReason: null, createdAt: '2026-11-18T10:00:00Z',
}
export const payment = { id: 'payment-1', paymentDate: '2026-11-20', amount: '50.00', method: 'BANK_TRANSFER', reference: 'Auszug 4', note: null, recordedAt: '2026-11-20T08:00:00Z', reversed: false, reversedAt: null, reversalReason: null }
