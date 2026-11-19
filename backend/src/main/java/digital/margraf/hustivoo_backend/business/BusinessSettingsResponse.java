package digital.margraf.hustivoo_backend.business;

import digital.margraf.hustivoo_backend.membership.MembershipRole;
import java.time.Instant;
import java.util.UUID;

/** Settings of one business plus the caller's role, so the client can render a read-only view for members. */
public record BusinessSettingsResponse(UUID id, String name, String legalForm, String industry, String currency,
        String street, String postalCode, String city, String country, String email, String phone, String website,
        String vatNumber, String iban, String bankName, String documentFooter, String brandColor, Logo logo,
        MembershipRole role, boolean editable, Instant updatedAt) {

    public record Logo(UUID id, int width, int height, int sizeBytes) {}

    static BusinessSettingsResponse from(Business business, BusinessLogo logo, MembershipRole role) {
        return new BusinessSettingsResponse(business.getId(), business.getName(), business.getLegalForm(), business.getIndustry(),
                business.getCurrency(), business.getStreet(), business.getPostalCode(), business.getCity(), business.getCountry(),
                business.getEmail(), business.getPhone(), business.getWebsite(), business.getVatNumber(), business.getIban(),
                business.getBankName(), business.getDocumentFooter(), business.getBrandColor(),
                logo == null ? null : new Logo(logo.getId(), logo.getWidth(), logo.getHeight(), logo.getSizeBytes()),
                role, role == MembershipRole.OWNER, business.getUpdatedAt());
    }
}
