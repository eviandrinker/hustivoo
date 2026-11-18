package digital.margraf.hustivoo_backend.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/businesses/{businessId}/customers")
public class CustomerController {
    private final CustomerService customers;
    public CustomerController(CustomerService customers) { this.customers = customers; }
    @GetMapping
    public List<CustomerResponse> list(@PathVariable UUID businessId,
            @RequestParam(defaultValue = "") @Size(max = 320) String search,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(required = false) CustomerStatus status, @RequestParam(required = false) CustomerType type,
            @RequestParam(defaultValue = "NAME_ASC") CustomerSort sort) {
        return customers.list(businessId, search, includeArchived, status, type, sort);
    }
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@PathVariable UUID businessId, @Valid @RequestBody CustomerRequest request) {
        CustomerResponse customer = customers.create(businessId, request);
        return ResponseEntity.created(URI.create("/api/businesses/" + businessId + "/customers/" + customer.id())).body(customer);
    }
    @GetMapping("/{customerId}")
    public CustomerResponse get(@PathVariable UUID businessId, @PathVariable UUID customerId) { return customers.get(businessId, customerId); }
    @PutMapping("/{customerId}")
    public CustomerResponse update(@PathVariable UUID businessId, @PathVariable UUID customerId, @Valid @RequestBody CustomerRequest request) {
        return customers.update(businessId, customerId, request);
    }
    @PatchMapping("/{customerId}/archive")
    public CustomerResponse archive(@PathVariable UUID businessId, @PathVariable UUID customerId) { return customers.archive(businessId, customerId, true); }
    @PatchMapping("/{customerId}/restore")
    public CustomerResponse restore(@PathVariable UUID businessId, @PathVariable UUID customerId) { return customers.archive(businessId, customerId, false); }
}
