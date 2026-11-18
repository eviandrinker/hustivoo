package digital.margraf.hustivoo_backend.servicecatalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/businesses/{businessId}/services")
public class ServiceController {
    private final ServiceCatalogService services;
    public ServiceController(ServiceCatalogService services) { this.services = services; }
    @GetMapping
    public List<ServiceResponse> list(@PathVariable UUID businessId,
            @RequestParam(defaultValue = "") @Size(max = 320) String search,
            @RequestParam(defaultValue = "") @Size(max = 120) String category,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(defaultValue = "NAME_ASC") ServiceSort sort) {
        return services.list(businessId, search, category, active, sort);
    }
    @PostMapping
    public ResponseEntity<ServiceResponse> create(@PathVariable UUID businessId, @Valid @RequestBody ServiceRequest request) {
        ServiceResponse service = services.create(businessId, request);
        return ResponseEntity.created(URI.create("/api/businesses/" + businessId + "/services/" + service.id())).body(service);
    }
    @GetMapping("/{serviceId}")
    public ServiceResponse get(@PathVariable UUID businessId, @PathVariable UUID serviceId) { return services.get(businessId, serviceId); }
    @PutMapping("/{serviceId}")
    public ServiceResponse update(@PathVariable UUID businessId, @PathVariable UUID serviceId, @Valid @RequestBody ServiceRequest request) {
        return services.update(businessId, serviceId, request);
    }
    @PatchMapping("/{serviceId}/archive")
    public ServiceResponse archive(@PathVariable UUID businessId, @PathVariable UUID serviceId) { return services.archive(businessId, serviceId, true); }
    @PatchMapping("/{serviceId}/restore")
    public ServiceResponse restore(@PathVariable UUID businessId, @PathVariable UUID serviceId) { return services.archive(businessId, serviceId, false); }
}
