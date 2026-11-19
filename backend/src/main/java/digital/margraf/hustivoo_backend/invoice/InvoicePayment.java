package digital.margraf.hustivoo_backend.invoice;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An incoming payment. Append-only: a wrong booking is reversed once (who, when, why) and booked again;
 * nothing is overwritten or deleted (enforced by {@code invoice_payments_guard}, V8).
 */
@Entity
@Table(name = "invoice_payments")
public class InvoicePayment {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;
    @Column(name = "payment_date", nullable = false, updatable = false)
    private LocalDate paymentDate;
    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false)
    private PaymentMethod method;
    @Column(length = 140, updatable = false)
    private String reference;
    @Column(length = 1000, updatable = false)
    private String note;
    @Column(name = "recorded_by", nullable = false, updatable = false)
    private UUID recordedBy;
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;
    @Column(name = "reversed_at")
    private Instant reversedAt;
    @Column(name = "reversed_by")
    private UUID reversedBy;
    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;

    protected InvoicePayment() {}

    InvoicePayment(Invoice invoice, LocalDate paymentDate, BigDecimal amount, PaymentMethod method, String reference,
            String note, UUID recordedBy, Instant now) {
        this.businessId = invoice.getBusinessId();
        this.invoiceId = invoice.getId();
        this.paymentDate = paymentDate;
        this.amount = amount.setScale(2);
        this.method = method;
        this.reference = reference;
        this.note = note;
        this.recordedBy = recordedBy;
        this.recordedAt = now;
    }

    void reverse(UUID user, String reason, Instant now) {
        if (reversedAt != null) throw new InvoiceConflictException("Payment already reversed");
        this.reversedAt = now;
        this.reversedBy = user;
        this.reversalReason = reason;
    }

    public boolean active() { return reversedAt == null; }
    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethod getMethod() { return method; }
    public String getReference() { return reference; }
    public String getNote() { return note; }
    public UUID getRecordedBy() { return recordedBy; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getReversedAt() { return reversedAt; }
    public String getReversalReason() { return reversalReason; }
}
