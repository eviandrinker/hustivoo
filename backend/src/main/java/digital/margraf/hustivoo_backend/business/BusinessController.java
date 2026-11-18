package digital.margraf.hustivoo_backend.business;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/businesses")
public class BusinessController {
    private final BusinessService businesses;

    public BusinessController(BusinessService businesses) { this.businesses = businesses; }

    @PostMapping
    public ResponseEntity<BusinessResponse> create(@Valid @RequestBody CreateBusinessRequest request) {
        BusinessResponse business = businesses.create(request);
        return ResponseEntity.created(URI.create("/api/businesses/" + business.id())).body(business);
    }

    @GetMapping
    public List<BusinessResponse> list() { return businesses.list(); }

    @GetMapping("/{id}")
    public BusinessResponse get(@PathVariable UUID id) { return businesses.get(id); }
}
