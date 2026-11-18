package digital.margraf.hustivoo_backend.customer;

import digital.margraf.hustivoo_backend.business.BusinessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@Validated
@Transactional(readOnly = true)
public class CustomerService {
    private final CustomerRepository customers;
    private final BusinessService businesses;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final CustomerHistory history;
    private final CustomerContactRepository contacts;
    public CustomerService(CustomerRepository customers, BusinessService businesses, org.springframework.jdbc.core.JdbcTemplate jdbc, CustomerHistory history, CustomerContactRepository contacts) {
        this.customers = customers;
        this.businesses = businesses;
        this.jdbc = jdbc; this.history = history; this.contacts = contacts;
    }
    // OWNER and MEMBER may read and write; all access goes through the existing membership check.
    private void authorize(UUID businessId) { businesses.get(businessId); }
    private Customer find(UUID businessId, UUID id) {
        return customers.findByIdAndBusinessId(id, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    public List<CustomerResponse> list(UUID businessId, String search, boolean includeArchived, CustomerStatus status, CustomerType type, CustomerSort sort) {
        authorize(businessId);
        return customers.search(businessId, search.strip(), includeArchived, status, type, sort.toSort()).stream().map(CustomerResponse::from).toList();
    }
    public CustomerResponse get(UUID businessId, UUID id) {
        authorize(businessId);
        return CustomerResponse.from(find(businessId, id));
    }
    @Transactional
    public CustomerResponse create(UUID businessId, @NotNull @Valid CustomerRequest request) {
        authorize(businessId);
        Long number = jdbc.queryForObject("""
                INSERT INTO customer_number_counters (business_id, last_value) VALUES (?, 1)
                ON CONFLICT (business_id) DO UPDATE SET last_value = customer_number_counters.last_value + 1
                RETURNING last_value
                """, Long.class, businessId);
        Customer customer = customers.saveAndFlush(new Customer(businessId, request, "K-" + String.format(java.util.Locale.ROOT, "%04d", number)));
        history.record(businessId, customer.getId(), "CREATED");
        return CustomerResponse.from(customer);
    }
    @Transactional
    public CustomerResponse update(UUID businessId, UUID id, @NotNull @Valid CustomerRequest request) {
        authorize(businessId);
        Customer customer = customers.lockScoped(id, businessId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request.type() == CustomerType.PRIVATE && !contacts.findAllByBusinessIdAndCustomerIdOrderByFirstNameAscIdAsc(businessId, id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        customer.update(request);
        history.record(businessId, id, "UPDATED");
        return CustomerResponse.from(customers.saveAndFlush(customer));
    }
    @Transactional
    public CustomerResponse archive(UUID businessId, UUID id, boolean archived) {
        authorize(businessId);
        Customer customer = customers.lockScoped(id, businessId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (customer.getArchived() != archived) {
            customer.setArchived(archived);
            history.record(businessId, id, archived ? "ARCHIVED" : "RESTORED");
        }
        return CustomerResponse.from(customers.saveAndFlush(customer));
    }
}
