package digital.margraf.hustivoo_backend.servicecatalog;

import digital.margraf.hustivoo_backend.business.BusinessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@Validated
@Transactional(readOnly = true)
public class ServiceCatalogService {
    private final ServiceRepository services;
    private final BusinessService businesses;
    public ServiceCatalogService(ServiceRepository services, BusinessService businesses) {
        this.services = services; this.businesses = businesses;
    }
    // Membership is resolved from the Security Context; OWNER and MEMBER have equal catalogue access.
    public List<ServiceResponse> list(UUID businessId, String search, String category, boolean active, ServiceSort sort) {
        businesses.get(businessId);
        return services.search(businessId, search.strip(), category.strip(), active, sort.toSort()).stream().map(ServiceResponse::from).toList();
    }
    public ServiceResponse get(UUID businessId, UUID id) {
        businesses.get(businessId);
        return ServiceResponse.from(services.findByIdAndBusinessId(id, businessId).orElseThrow(ServiceCatalogService::missing));
    }
    @Transactional
    public ServiceResponse create(UUID businessId, @NotNull @Valid ServiceRequest request) {
        businesses.get(businessId);
        return ServiceResponse.from(services.saveAndFlush(new CatalogService(businessId, request)));
    }
    @Transactional
    public ServiceResponse update(UUID businessId, UUID id, @NotNull @Valid ServiceRequest request) {
        businesses.get(businessId);
        CatalogService service = services.lockScoped(id, businessId).orElseThrow(ServiceCatalogService::missing);
        service.update(request);
        return ServiceResponse.from(services.saveAndFlush(service));
    }
    @Transactional
    public ServiceResponse archive(UUID businessId, UUID id, boolean archived) {
        businesses.get(businessId);
        CatalogService service = services.lockScoped(id, businessId).orElseThrow(ServiceCatalogService::missing);
        service.setActive(!archived);
        return ServiceResponse.from(services.saveAndFlush(service));
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
