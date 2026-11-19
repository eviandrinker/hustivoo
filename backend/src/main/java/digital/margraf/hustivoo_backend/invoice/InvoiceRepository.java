package digital.margraf.hustivoo_backend.invoice;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Every query is scoped by business ID; callers must authorize the business first. */
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByIdAndBusinessId(UUID id, UUID businessId);

    Optional<Invoice> findByQuoteIdAndBusinessId(UUID quoteId, UUID businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id and i.businessId = :businessId")
    Optional<Invoice> lockScoped(@Param("id") UUID id, @Param("businessId") UUID businessId);

    /**
     * Search by number or customer. {@code overdue} selects open invoices past their due date; a stored
     * status filter excludes overdue ones only where the caller asks for it (see InvoiceService).
     */
    @Query("""
            select i from Invoice i where i.businessId = :businessId
            and (:status is null or i.status = :status)
            and (:overdueOnly = false or (i.status in (digital.margraf.hustivoo_backend.invoice.InvoiceStatus.ISSUED,
                 digital.margraf.hustivoo_backend.invoice.InvoiceStatus.PARTIALLY_PAID) and i.dueDate < :today))
            and (locate(lower(:search), lower(i.invoiceNumber)) > 0
              or locate(lower(:search), lower(i.customerName)) > 0
              or locate(lower(:search), lower(coalesce(i.customerCompanyName, ''))) > 0)
            order by i.issueDate desc, i.invoiceNumber desc
            """)
    List<Invoice> search(@Param("businessId") UUID businessId, @Param("search") String search,
            @Param("status") InvoiceStatus status, @Param("overdueOnly") boolean overdueOnly, @Param("today") LocalDate today);
}
