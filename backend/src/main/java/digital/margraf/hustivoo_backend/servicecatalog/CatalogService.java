package digital.margraf.hustivoo_backend.servicecatalog;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "services")
public class CatalogService {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(length = 4000)
    private String description;
    @Column(length = 120)
    private String category;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ServiceUnit unit;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;
    @Column(nullable = false)
    private boolean active = true;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    protected CatalogService() {}
    public CatalogService(UUID businessId, ServiceRequest request) { this.businessId = businessId; update(request); }
    public void update(ServiceRequest request) {
        this.name = request.name();
        this.description = request.description();
        this.category = request.category();
        this.unit = request.unit();
        this.unitPrice = request.unitPrice();
        this.currency = request.currency();
        this.taxRate = request.taxRate();
    }
    public void setActive(boolean active) { this.active = active; }
    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public ServiceUnit getUnit() { return unit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getCurrency() { return currency; }
    public BigDecimal getTaxRate() { return taxRate; }
    public boolean getActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
