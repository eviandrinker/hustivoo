package digital.margraf.hustivoo_backend.membership;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    List<Membership> findAllByUser_Id(UUID userId);
    List<Membership> findAllByBusiness_Id(UUID businessId);
    java.util.Optional<Membership> findByBusiness_IdAndUser_Id(UUID businessId, UUID userId);
}
