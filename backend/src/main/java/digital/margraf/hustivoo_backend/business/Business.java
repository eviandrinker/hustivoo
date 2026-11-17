package digital.margraf.hustivoo_backend.business;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "businesses")
public class Business {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Business() {}

    public Business(String name) {
        this(name, "CHF");
    }

    public Business(String name, String currency) {
        rename(name);
        this.currency = java.util.Currency.getInstance(java.util.Objects.requireNonNull(currency)).getCurrencyCode();
    }

    public void rename(String name) {
        this.name = java.util.Objects.requireNonNull(name).strip();
        if (this.name.isBlank()) throw new IllegalArgumentException("Business name is required");
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
