package digital.margraf.hustivoo_backend.customer;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "customer_notes")
public class CustomerNote {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(name = "content", nullable = false, length = 4000)
    private String content;
    @Column(name = "created_by", nullable = true)
    private UUID createdBy;
    @Column(name = "author_name", nullable = false, length = 320)
    private String authorName;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    protected CustomerNote() {}
    public CustomerNote(UUID businessId, UUID customerId, String content, UUID createdBy, String authorName) {
        this.businessId = businessId;
        this.customerId = customerId;
        this.content = content;
        this.createdBy = createdBy;
        this.authorName = authorName;
    }
    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getCustomerId() { return customerId; }
    public String getContent() { return content; }
    public UUID getCreatedBy() { return createdBy; }
    public String getAuthorName() { return authorName; }
    public Instant getCreatedAt() { return createdAt; }
}
