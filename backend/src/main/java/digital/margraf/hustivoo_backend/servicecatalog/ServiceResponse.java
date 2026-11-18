package digital.margraf.hustivoo_backend.servicecatalog;

import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

// Decimal strings preserve exact amounts in JavaScript and future document snapshots.
public record ServiceResponse(UUID id, UUID businessId, String name, String description,
        String category, ServiceUnit unit, String unitPrice, String currency, String taxRate,
        boolean active, Instant createdAt, Instant updatedAt) {
    private static String decimal(BigDecimal value) { return value == null ? null : value.setScale(2).toPlainString(); }
    public static ServiceResponse from(CatalogService service) {
        return new ServiceResponse(service.getId(), service.getBusinessId(), service.getName(),
                service.getDescription(), service.getCategory(), service.getUnit(), decimal(service.getUnitPrice()),
                service.getCurrency(), decimal(service.getTaxRate()), service.getActive(), service.getCreatedAt(), service.getUpdatedAt());
    }
}
