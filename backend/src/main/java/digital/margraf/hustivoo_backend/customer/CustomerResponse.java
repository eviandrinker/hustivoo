package digital.margraf.hustivoo_backend.customer;

import java.util.UUID;
import java.time.Instant;

public record CustomerResponse(UUID id, UUID businessId, CustomerType type, String customerNumber, CustomerStatus status, String displayName, String companyName, String firstName, String lastName, String email, String phone, String street, String postalCode, String city, String country, String notes, boolean archived, Instant createdAt, Instant updatedAt) {
    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getBusinessId(), customer.getType(), customer.getCustomerNumber(), customer.getStatus(), customer.getDisplayName(), customer.getCompanyName(), customer.getFirstName(), customer.getLastName(), customer.getEmail(), customer.getPhone(), customer.getStreet(), customer.getPostalCode(), customer.getCity(), customer.getCountry(), customer.getNotes(), customer.getArchived(), customer.getCreatedAt(), customer.getUpdatedAt());
    }
}
