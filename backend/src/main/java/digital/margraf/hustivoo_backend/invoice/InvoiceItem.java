package digital.margraf.hustivoo_backend.invoice;

import digital.margraf.hustivoo_backend.quote.QuoteCalculator;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Immutable snapshot of one invoice position; drafts replace their items as a whole. */
@Entity
@Table(name = "invoice_items")
public class InvoiceItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;
    @Column(name = "service_id", updatable = false)
    private UUID serviceId;
    @Column(nullable = false, updatable = false)
    private int position;
    @Column(nullable = false, length = 4000, updatable = false)
    private String description;
    @Column(nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false)
    private ServiceUnit unit;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal unitPrice;
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2, updatable = false)
    private BigDecimal taxRate;
    @Column(name = "line_subtotal", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal lineSubtotal;
    @Column(name = "line_tax", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal lineTax;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal lineTotal;

    protected InvoiceItem() {}

    InvoiceItem(Invoice invoice, int position, UUID serviceId, String description, BigDecimal quantity,
            ServiceUnit unit, BigDecimal unitPrice, BigDecimal taxRate, QuoteCalculator.Line line) {
        this.invoice = invoice;
        this.businessId = invoice.getBusinessId();
        this.position = position;
        this.serviceId = serviceId;
        this.description = description;
        this.quantity = quantity.setScale(3);
        this.unit = unit;
        this.unitPrice = unitPrice.setScale(2);
        this.taxRate = taxRate.setScale(2);
        this.lineSubtotal = line.subtotal();
        this.lineTax = line.tax();
        this.lineTotal = line.total();
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getServiceId() { return serviceId; }
    public int getPosition() { return position; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public ServiceUnit getUnit() { return unit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getLineSubtotal() { return lineSubtotal; }
    public BigDecimal getLineTax() { return lineTax; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
