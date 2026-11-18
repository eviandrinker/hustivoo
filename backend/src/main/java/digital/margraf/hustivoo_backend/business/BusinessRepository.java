package digital.margraf.hustivoo_backend.business;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
    @org.springframework.data.jpa.repository.Query("""
            select m.business from Membership m where m.user.id = :userId
            order by m.business.createdAt, m.business.id
            """)
    java.util.List<Business> findAllForMember(@org.springframework.data.repository.query.Param("userId") UUID userId);

    @org.springframework.data.jpa.repository.Query("""
            select m.business from Membership m
            where m.business.id = :businessId and m.user.id = :userId
            """)
    java.util.Optional<Business> findForMember(
            @org.springframework.data.repository.query.Param("businessId") UUID businessId,
            @org.springframework.data.repository.query.Param("userId") UUID userId);
}
