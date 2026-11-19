package digital.margraf.hustivoo_backend.invoice;

import digital.margraf.hustivoo_backend.business.SenderProfile;
import digital.margraf.hustivoo_backend.customer.Customer;
import digital.margraf.hustivoo_backend.quote.QuoteCalculator;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "invoices")
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "quote_id", updatable = false)
    private UUID quoteId;
    @Column(name = "invoice_number", nullable = false, updatable = false, length = 32)
    private String invoiceNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private InvoiceStatus status = InvoiceStatus.DRAFT;
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;
    @Column(name = "payment_term_days", nullable = false)
    private int paymentTermDays;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "introduction_text", length = 4000)
    private String introductionText;
    @Column(name = "closing_text", length = 4000)
    private String closingText;
    @Column(length = 4000)
    private String notes;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "tax_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxTotal;
    @Column(name = "grand_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal grandTotal;
    @Column(name = "paid_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidTotal = BigDecimal.ZERO.setScale(2);
    @Column(name = "customer_number", nullable = false, length = 32) private String customerNumber;
    @Column(name = "customer_name", nullable = false, length = 255) private String customerName;
    @Column(name = "customer_company_name", length = 255) private String customerCompanyName;
    @Column(name = "customer_contact_name", length = 255) private String customerContactName;
    @Column(name = "customer_street", length = 255) private String customerStreet;
    @Column(name = "customer_postal_code", length = 20) private String customerPostalCode;
    @Column(name = "customer_city", length = 120) private String customerCity;
    @Column(name = "customer_country", nullable = false, length = 2) private String customerCountry;
    @Column(name = "customer_email", length = 320) private String customerEmail;
    @Column(name = "sender_name", nullable = false, length = 255) private String senderName;
    @Column(name = "sender_country", nullable = false, length = 2) private String senderCountry;
    @Column(name = "sender_street", length = 255) private String senderStreet;
    @Column(name = "sender_postal_code", length = 20) private String senderPostalCode;
    @Column(name = "sender_city", length = 120) private String senderCity;
    @Column(name = "sender_email", length = 320) private String senderEmail;
    @Column(name = "sender_phone", length = 40) private String senderPhone;
    @Column(name = "sender_website", length = 255) private String senderWebsite;
    @Column(name = "sender_vat_number", length = 40) private String senderVatNumber;
    @Column(name = "sender_iban", length = 34) private String senderIban;
    @Column(name = "sender_bank_name", length = 120) private String senderBankName;
    @Column(name = "sender_document_footer", length = 160) private String senderDocumentFooter;
    @Column(name = "sender_brand_color", length = 7) private String senderBrandColor;
    @Column(name = "sender_logo_id") private UUID senderLogoId;
    @Column(name = "issued_at") private Instant issuedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "cancellation_reason", length = 500) private String cancellationReason;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    protected Invoice() {}

    Invoice(UUID businessId, String invoiceNumber, UUID quoteId) {
        this.businessId = businessId;
        this.invoiceNumber = invoiceNumber;
        this.quoteId = quoteId;
    }

    /** The due date is always derived on the server: issue date + payment term. */
    void editHeader(LocalDate issueDate, int paymentTermDays, String currency, String introductionText,
            String closingText, String notes) {
        requireDraft();
        this.issueDate = issueDate;
        this.paymentTermDays = paymentTermDays;
        this.dueDate = issueDate.plusDays(paymentTermDays);
        this.currency = currency;
        this.introductionText = introductionText;
        this.closingText = closingText;
        this.notes = notes;
    }

    /** Customer data and sender name/country; refreshed while drafting and when issuing. */
    void snapshot(String senderName, String senderCountry, Customer customer) {
        requireDraft();
        this.senderName = senderName;
        this.senderCountry = senderCountry;
        this.customerId = customer.getId();
        this.customerNumber = customer.getCustomerNumber();
        this.customerName = customer.getDisplayName();
        this.customerCompanyName = customer.getCompanyName();
        String contact = Stream.of(customer.getFirstName(), customer.getLastName())
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(" "));
        this.customerContactName = contact.isBlank() ? null : contact;
        this.customerStreet = customer.getStreet();
        this.customerPostalCode = customer.getPostalCode();
        this.customerCity = customer.getCity();
        this.customerCountry = customer.getCountry();
        this.customerEmail = customer.getEmail();
    }

    void replaceItems(List<InvoiceItem> next, QuoteCalculator.Totals totals) {
        requireDraft();
        items.clear();
        items.addAll(next);
        this.subtotal = totals.subtotal();
        this.taxTotal = totals.taxTotal();
        this.grandTotal = totals.grandTotal();
    }

    /** Freezes the complete sender (address, payment details, branding, logo) for the issued document. */
    void issue(SenderProfile sender, Instant now) {
        requireDraft();
        if (items.isEmpty()) throw new InvoiceConflictException("Invoice has no items");
        if (grandTotal.signum() <= 0) throw new InvoiceConflictException("Invoice total must be positive");
        this.senderName = sender.name();
        this.senderCountry = sender.country();
        this.senderStreet = sender.street();
        this.senderPostalCode = sender.postalCode();
        this.senderCity = sender.city();
        this.senderEmail = sender.email();
        this.senderPhone = sender.phone();
        this.senderWebsite = sender.website();
        this.senderVatNumber = sender.vatNumber();
        this.senderIban = sender.iban();
        this.senderBankName = sender.bankName();
        this.senderDocumentFooter = sender.documentFooter();
        this.senderBrandColor = sender.brandColor();
        this.senderLogoId = sender.logoId();
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = now;
    }

    /** Only open invoices without active payments can be cancelled; reverse payments first. */
    void cancel(String reason, Instant now) {
        if (status != InvoiceStatus.ISSUED) {
            throw new InvoiceConflictException(status.open() ? "Reverse the payments before cancelling" : "Invalid status transition");
        }
        this.status = InvoiceStatus.CANCELLED;
        this.cancelledAt = now;
        this.cancellationReason = reason;
    }

    /** Sets the paid amount from the booked payments and derives the payment status. */
    void applyPaid(BigDecimal paid) {
        if (!status.open() && status != InvoiceStatus.PAID) throw new InvoiceConflictException("Invoice does not accept payments");
        if (paid.signum() < 0 || paid.compareTo(grandTotal) > 0) throw new InvoiceConflictException("Payment exceeds the open amount");
        this.paidTotal = paid.setScale(2);
        this.status = paid.signum() == 0 ? InvoiceStatus.ISSUED
                : paid.compareTo(grandTotal) == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID;
    }

    public BigDecimal openAmount() {
        return status == InvoiceStatus.DRAFT || status == InvoiceStatus.CANCELLED
                ? BigDecimal.ZERO.setScale(2) : grandTotal.subtract(paidTotal);
    }

    /** Derived: open amount after the due date. Never stored, so it cannot distort the accounting. */
    public boolean overdue(LocalDate today) {
        return status.open() && dueDate.isBefore(today);
    }

    void requireDraft() {
        if (status != InvoiceStatus.DRAFT) throw new InvoiceConflictException("Invoice is not editable");
    }

    /** Sender as frozen on issue. Drafts only carry name and country here; they print the current settings. */
    public SenderProfile frozenSender() {
        return new SenderProfile(senderName, senderCountry, senderStreet, senderPostalCode, senderCity, senderEmail,
                senderPhone, senderWebsite, senderVatNumber, senderIban, senderBankName, senderDocumentFooter,
                senderBrandColor, senderLogoId);
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getQuoteId() { return quoteId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public InvoiceStatus getStatus() { return status; }
    public LocalDate getIssueDate() { return issueDate; }
    public int getPaymentTermDays() { return paymentTermDays; }
    public LocalDate getDueDate() { return dueDate; }
    public String getCurrency() { return currency; }
    public String getIntroductionText() { return introductionText; }
    public String getClosingText() { return closingText; }
    public String getNotes() { return notes; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public BigDecimal getPaidTotal() { return paidTotal; }
    public String getCustomerNumber() { return customerNumber; }
    public String getCustomerName() { return customerName; }
    public String getCustomerCompanyName() { return customerCompanyName; }
    public String getCustomerContactName() { return customerContactName; }
    public String getCustomerStreet() { return customerStreet; }
    public String getCustomerPostalCode() { return customerPostalCode; }
    public String getCustomerCity() { return customerCity; }
    public String getCustomerCountry() { return customerCountry; }
    public String getCustomerEmail() { return customerEmail; }
    public String getSenderName() { return senderName; }
    public String getSenderCountry() { return senderCountry; }
    public UUID getSenderLogoId() { return senderLogoId; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getCancellationReason() { return cancellationReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<InvoiceItem> getItems() { return items; }
}
