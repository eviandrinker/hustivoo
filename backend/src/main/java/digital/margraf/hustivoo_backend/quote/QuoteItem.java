package digital.margraf.hustivoo_backend.quote;

import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Immutable snapshot of one quote position; drafts replace their items as a whole. */
@Entity
@Table(name = "quote_items")
public class QuoteItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false, updatable = false)
    private Quote quote;
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

    protected QuoteItem() {}

    QuoteItem(Quote quote, int position, UUID serviceId, String description, BigDecimal quantity,
            ServiceUnit unit, BigDecimal unitPrice, BigDecimal taxRate, QuoteCalculator.Line line) {
        this.quote = quote;
        this.businessId = quote.getBusinessId();
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
    public Quote getQuote() { return quote; }
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
