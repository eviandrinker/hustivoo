package digital.margraf.hustivoo_backend.quote;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Every query is scoped by business ID; callers must authorize the business first. */
public interface QuoteRepository extends JpaRepository<Quote, UUID> {
    Optional<Quote> findByIdAndBusinessId(UUID id, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quote q where q.id = :id and q.businessId = :businessId")
    Optional<Quote> lockScoped(@Param("id") UUID id, @Param("businessId") UUID businessId);

    @Query("""
            select q from Quote q where q.businessId = :businessId
            and (:status is null or q.status = :status)
            and (locate(lower(:search), lower(q.quoteNumber)) > 0
              or locate(lower(:search), lower(q.customerName)) > 0
              or locate(lower(:search), lower(coalesce(q.customerCompanyName, ''))) > 0)
            order by q.issueDate desc, q.quoteNumber desc
            """)
    List<Quote> search(@Param("businessId") UUID businessId, @Param("search") String search,
            @Param("status") QuoteStatus status);
}
