package digital.margraf.hustivoo_backend.invoice;

import digital.margraf.hustivoo_backend.quote.QuoteCalculator;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Amounts are decimal strings (scale 2; quantity up to 3 decimals) so JavaScript never loses precision.
 * {@code status} is the stored state, {@code displayStatus} adds the derived OVERDUE.
 */
public record InvoiceResponse(UUID id, UUID businessId, UUID customerId, String invoiceNumber, InvoiceStatus status,
        InvoiceStatus.Display displayStatus, boolean overdue, LocalDate issueDate, int paymentTermDays, LocalDate dueDate,
        String currency, String introductionText, String closingText, String notes, String subtotal, String taxTotal,
        String grandTotal, String paidTotal, String openAmount, CustomerSnapshot customer, List<Item> items,
        List<TaxGroup> taxGroups, List<Payment> payments, QuoteLink quote, QrBill qrBill, Instant issuedAt,
        Instant cancelledAt, String cancellationReason, Instant createdAt, Instant updatedAt) {

    public record CustomerSnapshot(String customerNumber, String name, String companyName, String contactName,
            String street, String postalCode, String city, String country, String email) {}

    public record Item(UUID id, UUID serviceId, int position, String description, String quantity, ServiceUnit unit,
            String unitPrice, String taxRate, String lineSubtotal, String lineTax, String lineTotal) {}

    public record TaxGroup(String rate, String base, String tax) {}

    public record Payment(UUID id, LocalDate paymentDate, String amount, PaymentMethod method, String reference,
            String note, Instant recordedAt, boolean reversed, Instant reversedAt, String reversalReason) {
        static Payment from(InvoicePayment payment) {
            return new Payment(payment.getId(), payment.getPaymentDate(), money(payment.getAmount()), payment.getMethod(),
                    payment.getReference(), payment.getNote(), payment.getRecordedAt(), !payment.active(),
                    payment.getReversedAt(), payment.getReversalReason());
        }
    }

    public record QuoteLink(UUID id, String quoteNumber) {}

    /** Whether the PDF contains a Swiss QR payment part, and if not, why (German, user-facing). */
    public record QrBill(boolean available, List<String> problems) {}

    /** List row without positions, payments or internal notes. */
    public record Summary(UUID id, UUID businessId, UUID customerId, String invoiceNumber, InvoiceStatus status,
            InvoiceStatus.Display displayStatus, boolean overdue, String customerName, LocalDate issueDate,
            LocalDate dueDate, String currency, String grandTotal, String paidTotal, String openAmount, Instant updatedAt) {
        static Summary from(Invoice invoice, LocalDate today) {
            boolean overdue = invoice.overdue(today);
            return new Summary(invoice.getId(), invoice.getBusinessId(), invoice.getCustomerId(), invoice.getInvoiceNumber(),
                    invoice.getStatus(), invoice.getStatus().display(overdue), overdue, invoice.getCustomerName(),
                    invoice.getIssueDate(), invoice.getDueDate(), invoice.getCurrency(), money(invoice.getGrandTotal()),
                    money(invoice.getPaidTotal()), money(invoice.openAmount()), invoice.getUpdatedAt());
        }
    }

    static String money(BigDecimal value) { return value.setScale(2).toPlainString(); }

    static String quantity(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    /** Exact per-rate sums of the rounded line values, ascending by rate. */
    static List<QuoteCalculator.TaxGroup> taxGroups(List<InvoiceItem> items) {
        Map<BigDecimal, BigDecimal[]> groups = new TreeMap<>();
        for (InvoiceItem item : items) {
            BigDecimal[] sums = groups.computeIfAbsent(item.getTaxRate().setScale(2),
                    rate -> new BigDecimal[] {BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)});
            sums[0] = sums[0].add(item.getLineSubtotal());
            sums[1] = sums[1].add(item.getLineTax());
        }
        return groups.entrySet().stream().map(entry -> new QuoteCalculator.TaxGroup(entry.getKey(), entry.getValue()[0], entry.getValue()[1])).toList();
    }

    static InvoiceResponse from(Invoice invoice, List<InvoicePayment> payments, QuoteLink quote, QrBill qrBill, LocalDate today) {
        List<Item> items = invoice.getItems().stream().map(item -> new Item(item.getId(), item.getServiceId(),
                item.getPosition(), item.getDescription(), quantity(item.getQuantity()), item.getUnit(),
                money(item.getUnitPrice()), money(item.getTaxRate()), money(item.getLineSubtotal()),
                money(item.getLineTax()), money(item.getLineTotal()))).toList();
        List<TaxGroup> groups = taxGroups(invoice.getItems()).stream()
                .map(group -> new TaxGroup(money(group.rate()), money(group.base()), money(group.tax()))).toList();
        boolean overdue = invoice.overdue(today);
        return new InvoiceResponse(invoice.getId(), invoice.getBusinessId(), invoice.getCustomerId(), invoice.getInvoiceNumber(),
                invoice.getStatus(), invoice.getStatus().display(overdue), overdue, invoice.getIssueDate(),
                invoice.getPaymentTermDays(), invoice.getDueDate(), invoice.getCurrency(), invoice.getIntroductionText(),
                invoice.getClosingText(), invoice.getNotes(), money(invoice.getSubtotal()), money(invoice.getTaxTotal()),
                money(invoice.getGrandTotal()), money(invoice.getPaidTotal()), money(invoice.openAmount()),
                new CustomerSnapshot(invoice.getCustomerNumber(), invoice.getCustomerName(), invoice.getCustomerCompanyName(),
                        invoice.getCustomerContactName(), invoice.getCustomerStreet(), invoice.getCustomerPostalCode(),
                        invoice.getCustomerCity(), invoice.getCustomerCountry(), invoice.getCustomerEmail()),
                items, groups, payments.stream().map(Payment::from).toList(), quote, qrBill, invoice.getIssuedAt(),
                invoice.getCancelledAt(), invoice.getCancellationReason(), invoice.getCreatedAt(), invoice.getUpdatedAt());
    }
}
