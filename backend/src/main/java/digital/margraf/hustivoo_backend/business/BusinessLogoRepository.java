package digital.margraf.hustivoo_backend.business;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessLogoRepository extends JpaRepository<BusinessLogo, UUID> {
    /** Every lookup is scoped by business; a foreign logo ID behaves exactly like an unknown one. */
    Optional<BusinessLogo> findByIdAndBusinessId(UUID id, UUID businessId);

    /** Removes a replaced logo unless it is still current or frozen into a quote snapshot. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM business_logos l WHERE l.id = :id AND l.business_id = :businessId
              AND NOT EXISTS (SELECT 1 FROM businesses b WHERE b.logo_id = l.id)
              AND NOT EXISTS (SELECT 1 FROM quotes q WHERE q.sender_logo_id = l.id)
            """, nativeQuery = true)
    int deleteIfUnused(@Param("id") UUID id, @Param("businessId") UUID businessId);
}
