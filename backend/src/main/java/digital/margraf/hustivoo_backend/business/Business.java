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

    @Column(length = 255) private String street;
    @Column(name = "postal_code", length = 20) private String postalCode;
    @Column(length = 120) private String city;
    @Column(length = 320) private String email;
    @Column(length = 40) private String phone;
    @Column(length = 255) private String website;
    @Column(name = "vat_number", length = 40) private String vatNumber;
    @Column(length = 34) private String iban;
    @Column(name = "bank_name", length = 120) private String bankName;
    @Column(name = "document_footer", length = 160) private String documentFooter;
    @Column(name = "brand_color", length = 7) private String brandColor;
    @Column(name = "logo_id") private UUID logoId;

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

    /** Applies validated settings; the currency is fixed after onboarding because documents depend on it. */
    void updateSettings(BusinessSettingsRequest settings) {
        rename(settings.name());
        this.industry = settings.industry();
        this.legalForm = settings.legalForm();
        this.country = settings.country();
        this.street = settings.street();
        this.postalCode = settings.postalCode();
        this.city = settings.city();
        this.email = settings.email();
        this.phone = settings.phone();
        this.website = settings.website();
        this.vatNumber = settings.vatNumber();
        this.iban = settings.iban();
        this.bankName = settings.bankName();
        this.documentFooter = settings.documentFooter();
        this.brandColor = settings.brandColor();
    }

    void useLogo(UUID logoId) { this.logoId = logoId; }

    public String getStreet() { return street; }
    public String getPostalCode() { return postalCode; }
    public String getCity() { return city; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getWebsite() { return website; }
    public String getVatNumber() { return vatNumber; }
    public String getIban() { return iban; }
    public String getBankName() { return bankName; }
    public String getDocumentFooter() { return documentFooter; }
    public String getBrandColor() { return brandColor; }
    public UUID getLogoId() { return logoId; }
    public String getIndustry() { return industry; }
    public String getLegalForm() { return legalForm; }
    public String getCountry() { return country; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
