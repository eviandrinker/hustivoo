package digital.margraf.hustivoo_backend.servicecatalog;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Sort;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface ServiceRepository extends JpaRepository<CatalogService, UUID> {
    Optional<CatalogService> findByIdAndBusinessId(UUID id, UUID businessId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CatalogService s where s.id = :id and s.businessId = :businessId")
    Optional<CatalogService> lockScoped(@Param("id") UUID id, @Param("businessId") UUID businessId);
    @Query("""
            select s from CatalogService s where s.businessId = :businessId and s.active = :active
            and (:category = '' or lower(s.category) = lower(:category))
            and (locate(lower(:search), lower(s.name)) > 0
              or locate(lower(:search), lower(coalesce(s.description, ''))) > 0)
            """)
    List<CatalogService> search(@Param("businessId") UUID businessId, @Param("search") String search,
            @Param("category") String category, @Param("active") boolean active, Sort sort);
}
