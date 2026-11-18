package digital.margraf.hustivoo_backend.customer;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "customer_activities")
public class CustomerActivity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(name = "event", nullable = false, length = 40)
    private String event;
    @Column(name = "created_by", nullable = true)
    private UUID createdBy;
    @Column(name = "author_name", nullable = false, length = 320)
    private String authorName;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    protected CustomerActivity() {}
    public CustomerActivity(UUID businessId, UUID customerId, String event, UUID createdBy, String authorName) {
        this.businessId = businessId;
        this.customerId = customerId;
        this.event = event;
        this.createdBy = createdBy;
        this.authorName = authorName;
    }
    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getCustomerId() { return customerId; }
    public String getEvent() { return event; }
    public UUID getCreatedBy() { return createdBy; }
    public String getAuthorName() { return authorName; }
    public Instant getCreatedAt() { return createdAt; }
}
