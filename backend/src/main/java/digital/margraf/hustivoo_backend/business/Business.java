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

    @Column(length = 120)
    private String industry;

    @Column(name = "legal_form", length = 120)
    private String legalForm;

    @Column(nullable = false, length = 2)
    private String country;

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
        this(name, null, null, "CH", currency);
    }

    public Business(String name, String industry, String legalForm, String country, String currency) {
        rename(name);
        this.industry = industry;
        this.legalForm = legalForm;
        this.country = java.util.Objects.requireNonNull(country);
        this.currency = java.util.Currency.getInstance(java.util.Objects.requireNonNull(currency)).getCurrencyCode();
    }

    public void rename(String name) {
        this.name = java.util.Objects.requireNonNull(name).strip();
        if (this.name.isBlank()) throw new IllegalArgumentException("Business name is required");
    }

    public String getIndustry() { return industry; }
    public String getLegalForm() { return legalForm; }
    public String getCountry() { return country; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
