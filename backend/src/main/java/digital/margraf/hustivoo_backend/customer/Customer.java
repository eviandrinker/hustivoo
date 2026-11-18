package digital.margraf.hustivoo_backend.customer;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "customers")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private CustomerType type;
    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;
    @Column(name = "company_name", length = 255)
    private String companyName;
    @Column(name = "first_name", length = 120)
    private String firstName;
    @Column(name = "last_name", length = 120)
    private String lastName;
    @Column(name = "email", length = 320)
    private String email;
    @Column(name = "phone", length = 50)
    private String phone;
    @Column(name = "street", length = 255)
    private String street;
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    @Column(name = "city", length = 120)
    private String city;
    @Column(name = "country", length = 2, nullable = false)
    private String country;
    @Column(name = "notes", length = 4000)
    private String notes;
    @Column(name = "customer_number", nullable = false, updatable = false, length = 32)
    private String customerNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private CustomerStatus status = CustomerStatus.ACTIVE;
    @Column(nullable = false)
    private boolean archived;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    protected Customer() {}
    public Customer(UUID businessId, CustomerRequest request, String customerNumber) {
        this.businessId = businessId;
        this.customerNumber = customerNumber;
        update(request);
    }
    public void update(CustomerRequest request) {
        this.type = request.type();
        this.displayName = request.displayName();
        this.companyName = request.companyName();
        this.firstName = request.firstName();
        this.lastName = request.lastName();
        this.email = request.email();
        this.phone = request.phone();
        this.street = request.street();
        this.postalCode = request.postalCode();
        this.city = request.city();
        this.country = request.country();
        this.notes = request.notes();
    }
    public String getCustomerNumber() { return customerNumber; }
    public CustomerStatus getStatus() { return status; }
    public void setStatus(CustomerStatus status) { this.status = status; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public CustomerType getType() { return type; }
    public String getDisplayName() { return displayName; }
    public String getCompanyName() { return companyName; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getStreet() { return street; }
    public String getPostalCode() { return postalCode; }
    public String getCity() { return city; }
    public String getCountry() { return country; }
    public String getNotes() { return notes; }
    public boolean getArchived() { return archived; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
