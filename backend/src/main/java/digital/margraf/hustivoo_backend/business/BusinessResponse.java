package digital.margraf.hustivoo_backend.business;

import java.time.Instant;
import java.util.UUID;

public record BusinessResponse(UUID id, String name, String industry, String legalForm,
        String country, String currency, Instant createdAt, Instant updatedAt) {
    static BusinessResponse from(Business business) {
        return new BusinessResponse(business.getId(), business.getName(), business.getIndustry(),
                business.getLegalForm(), business.getCountry(), business.getCurrency(),
                business.getCreatedAt(), business.getUpdatedAt());
    }
}
