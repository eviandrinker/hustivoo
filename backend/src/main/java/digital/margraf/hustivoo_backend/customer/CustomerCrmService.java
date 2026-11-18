package digital.margraf.hustivoo_backend.customer;

import digital.margraf.hustivoo_backend.business.BusinessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import static digital.margraf.hustivoo_backend.customer.CrmDtos.*;

@Service
@Validated
@Transactional(readOnly = true)
public class CustomerCrmService {
    private final BusinessService businesses;
    private final CustomerRepository customers;
    private final CustomerContactRepository contacts;
    private final CustomerNoteRepository notes;
    private final CustomerActivityRepository activities;
    private final CustomerHistory history;
    public CustomerCrmService(BusinessService businesses, CustomerRepository customers, CustomerContactRepository contacts,
            CustomerNoteRepository notes, CustomerActivityRepository activities, CustomerHistory history) {
        this.businesses=businesses; this.customers=customers; this.contacts=contacts; this.notes=notes; this.activities=activities; this.history=history;
    }
    private Customer scoped(UUID business, UUID customer, boolean lock) {
        businesses.get(business);
        return (lock ? customers.lockScoped(customer, business) : customers.findByIdAndBusinessId(customer, business))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @Transactional
    public CustomerResponse status(UUID business, UUID customer, @NotNull @Valid StatusRequest request) {
        Customer entity = scoped(business, customer, true);
        if (entity.getStatus() != request.status()) {
            entity.setStatus(request.status()); history.record(business, customer, "STATUS_CHANGED");
        }
        return CustomerResponse.from(customers.saveAndFlush(entity));
    }
    public List<ContactResponse> contacts(UUID business, UUID customer) {
        scoped(business, customer, false);
        return contacts.findAllByBusinessIdAndCustomerIdOrderByFirstNameAscIdAsc(business, customer).stream().map(ContactResponse::from).toList();
    }
    @Transactional
    public ContactResponse saveContact(UUID business, UUID customer, UUID id, @NotNull @Valid ContactRequest request) {
        Customer parent = scoped(business, customer, true);
        CustomerContact contact = id == null ? null : contacts.findByIdAndBusinessIdAndCustomerId(id, business, customer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (parent.getType() != CustomerType.COMPANY) throw new ResponseStatusException(HttpStatus.CONFLICT);
        if (request.primaryContact()) {
            contacts.findAllByBusinessIdAndCustomerIdOrderByFirstNameAscIdAsc(business, customer).stream()
                    .filter(c -> !c.getId().equals(id)).forEach(CustomerContact::demote);
            contacts.flush(); // Remove the previous primary before setting the new primary.
        }
        if (contact == null) contact = new CustomerContact(business, customer, request.firstName(), request.lastName(), request.jobTitle(), request.email(), request.phone(), request.primaryContact());
        else contact.update(request);
        contact = contacts.saveAndFlush(contact);
        history.record(business, customer, id == null ? "CONTACT_CREATED" : "CONTACT_UPDATED");
        return ContactResponse.from(contact);
    }
    @Transactional
    public void removeContact(UUID business, UUID customer, UUID id) {
        scoped(business, customer, true);
        var contact = contacts.findByIdAndBusinessIdAndCustomerId(id, business, customer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        contacts.delete(contact);
        history.record(business, customer, "CONTACT_REMOVED");
    }
    public List<NoteResponse> notes(UUID business, UUID customer) {
        scoped(business, customer, false);
        return notes.findAllByBusinessIdAndCustomerIdOrderByCreatedAtDescIdDesc(business, customer).stream().map(NoteResponse::from).toList();
    }
    @Transactional
    public NoteResponse addNote(UUID business, UUID customer, @NotNull @Valid NoteRequest request) {
        scoped(business, customer, true);
        var actor = history.actor();
        var note = notes.saveAndFlush(new CustomerNote(business, customer, request.content(), actor.getId(), actor.getEmail()));
        history.record(business, customer, "NOTE_ADDED");
        return NoteResponse.from(note);
    }
    public List<ActivityResponse> activities(UUID business, UUID customer) {
        scoped(business, customer, false);
        return activities.findAllByBusinessIdAndCustomerIdOrderByCreatedAtDescIdDesc(business, customer).stream().map(ActivityResponse::from).toList();
    }
}
