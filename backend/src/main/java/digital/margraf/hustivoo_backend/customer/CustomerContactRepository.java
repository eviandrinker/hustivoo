package digital.margraf.hustivoo_backend.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CustomerContactRepository extends JpaRepository<CustomerContact, UUID> {
    List<CustomerContact> findAllByBusinessIdAndCustomerIdOrderByFirstNameAscIdAsc(UUID businessId, UUID customerId);
    Optional<CustomerContact> findByIdAndBusinessIdAndCustomerId(UUID id, UUID businessId, UUID customerId);
}
