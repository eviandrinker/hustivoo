package digital.margraf.hustivoo_backend.business;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Immutable, normalised PNG logo owned by one business (see V7). */
@Entity
@Table(name = "business_logos")
public class BusinessLogo {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, updatable = false)
    private byte[] content;
    @Column(name = "content_type", nullable = false, updatable = false, length = 32)
    private String contentType;
    @Column(nullable = false, updatable = false)
    private int width;
    @Column(nullable = false, updatable = false)
    private int height;
    @Column(name = "size_bytes", nullable = false, updatable = false)
    private int sizeBytes;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BusinessLogo() {}

    BusinessLogo(UUID businessId, LogoImages.Normalized image) {
        this.businessId = businessId;
        this.content = image.png();
        this.contentType = "image/png";
        this.width = image.width();
        this.height = image.height();
        this.sizeBytes = image.png().length;
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public byte[] getContent() { return content; }
    public String getContentType() { return contentType; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
