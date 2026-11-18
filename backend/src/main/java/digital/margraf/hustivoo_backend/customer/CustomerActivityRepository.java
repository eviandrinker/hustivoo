package digital.margraf.hustivoo_backend.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CustomerActivityRepository extends JpaRepository<CustomerActivity, UUID> {
    List<CustomerActivity> findAllByBusinessIdAndCustomerIdOrderByCreatedAtDescIdDesc(UUID businessId, UUID customerId);
}
