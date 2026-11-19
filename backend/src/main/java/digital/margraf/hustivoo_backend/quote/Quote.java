package digital.margraf.hustivoo_backend.quote;

import digital.margraf.hustivoo_backend.business.SenderProfile;
import digital.margraf.hustivoo_backend.customer.Customer;
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
@Table(name = "quotes")
public class Quote {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "quote_number", nullable = false, updatable = false, length = 32)
    private String quoteNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private QuoteStatus status = QuoteStatus.DRAFT;
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;
    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;
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
    @Column(name = "sender_name", nullable = false, length = 255)
    private String senderName;
    @Column(name = "sender_country", nullable = false, length = 2)
    private String senderCountry;
    // Frozen sender details (V7). Filled when the quote is sent; drafts print the current settings.
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
    @Column(name = "customer_number", nullable = false, length = 32)
    private String customerNumber;
    @Column(name = "customer_name", nullable = false, length = 255)
    private String customerName;
    @Column(name = "customer_company_name", length = 255)
    private String customerCompanyName;
    @Column(name = "customer_contact_name", length = 255)
    private String customerContactName;
    @Column(name = "customer_street", length = 255)
    private String customerStreet;
    @Column(name = "customer_postal_code", length = 20)
    private String customerPostalCode;
    @Column(name = "customer_city", length = 120)
    private String customerCity;
    @Column(name = "customer_country", nullable = false, length = 2)
    private String customerCountry;
    @Column(name = "customer_email", length = 320)
    private String customerEmail;
    @Column(name = "sent_at")
    private Instant sentAt;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuoteItem> items = new ArrayList<>();

    protected Quote() {}

    Quote(UUID businessId, String quoteNumber) {
        this.businessId = businessId;
        this.quoteNumber = quoteNumber;
    }

    void editHeader(LocalDate issueDate, LocalDate validUntil, String currency,
            String introductionText, String closingText, String notes) {
        requireDraft();
        this.issueDate = issueDate;
        this.validUntil = validUntil;
        this.currency = currency;
        this.introductionText = introductionText;
        this.closingText = closingText;
        this.notes = notes;
    }

    /** Copies the master data needed for the document; refreshed while drafting and when sending. */
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

    /** Freezes the complete sender (address, payment details, branding, logo) when the quote is issued. */
    void freezeSender(SenderProfile sender) {
        requireDraft();
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
    }

    /** The sender as frozen on this quote. Quotes issued before V7 only carry name and country. */
    public SenderProfile frozenSender() {
        return new SenderProfile(senderName, senderCountry, senderStreet, senderPostalCode, senderCity, senderEmail,
                senderPhone, senderWebsite, senderVatNumber, senderIban, senderBankName, senderDocumentFooter,
                senderBrandColor, senderLogoId);
    }

    void replaceItems(List<QuoteItem> next, QuoteCalculator.Totals totals) {
        requireDraft();
        items.clear();
        items.addAll(next);
        this.subtotal = totals.subtotal();
        this.taxTotal = totals.taxTotal();
        this.grandTotal = totals.grandTotal();
    }

    void transitionTo(QuoteStatus target, Instant now) {
        if (!status.canTransitionTo(target)) throw new QuoteConflictException("Invalid status transition");
        if (target == QuoteStatus.SENT) sentAt = now; else decidedAt = now;
        status = target;
    }

    void requireDraft() {
        if (!status.editable()) throw new QuoteConflictException("Quote is not editable");
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getCustomerId() { return customerId; }
    public String getQuoteNumber() { return quoteNumber; }
    public QuoteStatus getStatus() { return status; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getCurrency() { return currency; }
    public String getIntroductionText() { return introductionText; }
    public String getClosingText() { return closingText; }
    public String getNotes() { return notes; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTaxTotal() { return taxTotal; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public String getSenderName() { return senderName; }
    public String getSenderCountry() { return senderCountry; }
    public UUID getSenderLogoId() { return senderLogoId; }
    public String getCustomerNumber() { return customerNumber; }
    public String getCustomerName() { return customerName; }
    public String getCustomerCompanyName() { return customerCompanyName; }
    public String getCustomerContactName() { return customerContactName; }
    public String getCustomerStreet() { return customerStreet; }
    public String getCustomerPostalCode() { return customerPostalCode; }
    public String getCustomerCity() { return customerCity; }
    public String getCustomerCountry() { return customerCountry; }
    public String getCustomerEmail() { return customerEmail; }
    public Instant getSentAt() { return sentAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<QuoteItem> getItems() { return items; }
}
