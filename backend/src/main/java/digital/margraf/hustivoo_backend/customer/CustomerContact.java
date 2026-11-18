package digital.margraf.hustivoo_backend.customer;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "customer_contacts")
public class CustomerContact {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;
    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;
    @Column(name = "job_title", nullable = true, length = 120)
    private String jobTitle;
    @Column(name = "email", nullable = true, length = 320)
    private String email;
    @Column(name = "phone", nullable = true, length = 50)
    private String phone;
    @Column(name = "primary_contact", nullable = false)
    private boolean primaryContact;
    protected CustomerContact() {}
    public CustomerContact(UUID businessId, UUID customerId, String firstName, String lastName, String jobTitle, String email, String phone, boolean primaryContact) {
        this.businessId = businessId;
        this.customerId = customerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.jobTitle = jobTitle;
        this.email = email;
        this.phone = phone;
        this.primaryContact = primaryContact;
    }
    public void update(CrmDtos.ContactRequest request) {
        this.firstName = request.firstName();
        this.lastName = request.lastName();
        this.jobTitle = request.jobTitle();
        this.email = request.email();
        this.phone = request.phone();
        this.primaryContact = request.primaryContact();
    }
    public void demote() { primaryContact = false; }
    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public UUID getCustomerId() { return customerId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getJobTitle() { return jobTitle; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public boolean getPrimaryContact() { return primaryContact; }
}
