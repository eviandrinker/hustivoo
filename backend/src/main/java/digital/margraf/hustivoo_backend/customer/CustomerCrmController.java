package digital.margraf.hustivoo_backend.customer;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
import static digital.margraf.hustivoo_backend.customer.CrmDtos.*;

@RestController
@RequestMapping("/api/businesses/{businessId}/customers/{customerId}")
public class CustomerCrmController {
    private final CustomerCrmService crm;
    public CustomerCrmController(CustomerCrmService crm) { this.crm = crm; }
    @PatchMapping("/status")
    public CustomerResponse status(@PathVariable UUID businessId, @PathVariable UUID customerId, @Valid @RequestBody StatusRequest request) { return crm.status(businessId, customerId, request); }
    @GetMapping("/contacts")
    public List<ContactResponse> contacts(@PathVariable UUID businessId, @PathVariable UUID customerId) { return crm.contacts(businessId, customerId); }
    @PostMapping("/contacts") @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse createContact(@PathVariable UUID businessId, @PathVariable UUID customerId, @Valid @RequestBody ContactRequest request) { return crm.saveContact(businessId, customerId, null, request); }
    @PutMapping("/contacts/{contactId}")
    public ContactResponse updateContact(@PathVariable UUID businessId, @PathVariable UUID customerId, @PathVariable UUID contactId, @Valid @RequestBody ContactRequest request) { return crm.saveContact(businessId, customerId, contactId, request); }
    @DeleteMapping("/contacts/{contactId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContact(@PathVariable UUID businessId, @PathVariable UUID customerId, @PathVariable UUID contactId) { crm.removeContact(businessId, customerId, contactId); }
    @GetMapping("/notes")
    public List<NoteResponse> notes(@PathVariable UUID businessId, @PathVariable UUID customerId) { return crm.notes(businessId, customerId); }
    @PostMapping("/notes") @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse addNote(@PathVariable UUID businessId, @PathVariable UUID customerId, @Valid @RequestBody NoteRequest request) { return crm.addNote(businessId, customerId, request); }
    @GetMapping("/activities")
    public List<ActivityResponse> activities(@PathVariable UUID businessId, @PathVariable UUID customerId) { return crm.activities(businessId, customerId); }
}
