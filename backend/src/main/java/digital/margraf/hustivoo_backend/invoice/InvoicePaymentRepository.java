package digital.margraf.hustivoo_backend.invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {
    List<InvoicePayment> findAllByInvoiceIdAndBusinessIdOrderByPaymentDateAscRecordedAtAsc(UUID invoiceId, UUID businessId);

    Optional<InvoicePayment> findByIdAndInvoiceIdAndBusinessId(UUID id, UUID invoiceId, UUID businessId);
}
