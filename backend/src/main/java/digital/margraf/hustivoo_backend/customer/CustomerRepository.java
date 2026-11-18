package digital.margraf.hustivoo_backend.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByIdAndBusinessId(UUID id, UUID businessId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id and c.businessId = :businessId")
    Optional<Customer> lockScoped(@Param("id") UUID id, @Param("businessId") UUID businessId);

    @Query("""
            select c from Customer c where c.businessId = :businessId
            and (:includeArchived = true or c.archived = false)
            and (:status is null or c.status = :status)
            and (:type is null or c.type = :type)
            and (locate(lower(:search), lower(c.displayName)) > 0
              or locate(lower(:search), lower(coalesce(c.companyName, ''))) > 0
              or locate(lower(:search), lower(coalesce(c.email, ''))) > 0
              or locate(lower(:search), lower(c.customerNumber)) > 0)
            """)
    List<Customer> search(@Param("businessId") UUID businessId,
            @Param("search") String search, @Param("includeArchived") boolean includeArchived,
            @Param("status") CustomerStatus status, @Param("type") CustomerType type, org.springframework.data.domain.Sort sort);
}
