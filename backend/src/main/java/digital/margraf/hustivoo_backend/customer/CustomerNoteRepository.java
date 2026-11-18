package digital.margraf.hustivoo_backend.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CustomerNoteRepository extends JpaRepository<CustomerNote, UUID> {
    List<CustomerNote> findAllByBusinessIdAndCustomerIdOrderByCreatedAtDescIdDesc(UUID businessId, UUID customerId);
}
