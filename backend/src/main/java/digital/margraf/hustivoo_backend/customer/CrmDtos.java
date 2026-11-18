package digital.margraf.hustivoo_backend.customer;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class CrmDtos {
    private CrmDtos() {}
    public record StatusRequest(@NotNull CustomerStatus status) {}
    public record ContactRequest(@NotBlank @Size(max=120) String firstName,
            @NotBlank @Size(max=120) String lastName, @Size(max=120) String jobTitle,
            @Email @Size(max=320) String email, @Size(max=50) String phone, boolean primaryContact) {
        public ContactRequest {
            firstName = clean(firstName); lastName = clean(lastName); jobTitle = clean(jobTitle);
            email = clean(email); phone = clean(phone);
        }
    }
    public record NoteRequest(@NotBlank @Size(max=4000) String content) {
        public NoteRequest { content = clean(content); }
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    public record ContactResponse(UUID id, UUID businessId, UUID customerId, String firstName,
            String lastName, String jobTitle, String email, String phone, boolean primaryContact) {
        static ContactResponse from(CustomerContact c) { return new ContactResponse(c.getId(), c.getBusinessId(), c.getCustomerId(), c.getFirstName(), c.getLastName(), c.getJobTitle(), c.getEmail(), c.getPhone(), c.getPrimaryContact()); }
    }
    public record NoteResponse(UUID id, UUID businessId, UUID customerId, String content, UUID createdBy, String authorName, Instant createdAt) {
        static NoteResponse from(CustomerNote n) { return new NoteResponse(n.getId(), n.getBusinessId(), n.getCustomerId(), n.getContent(), n.getCreatedBy(), n.getAuthorName(), n.getCreatedAt()); }
    }
    public record ActivityResponse(UUID id, UUID businessId, UUID customerId, String event, UUID createdBy, String authorName, Instant createdAt) {
        static ActivityResponse from(CustomerActivity a) { return new ActivityResponse(a.getId(), a.getBusinessId(), a.getCustomerId(), a.getEvent(), a.getCreatedBy(), a.getAuthorName(), a.getCreatedAt()); }
    }
}
