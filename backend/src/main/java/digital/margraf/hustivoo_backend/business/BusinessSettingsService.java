package digital.margraf.hustivoo_backend.business;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

/**
 * Business settings and logo. Membership is resolved from the Security Context for every call
 * (non-members get 404); reading is open to OWNER and MEMBER, every change requires OWNER (403).
 */
@Service
@Validated
@Transactional(readOnly = true)
public class BusinessSettingsService {
    private final BusinessService businesses;
    private final BusinessRepository repository;
    private final BusinessLogoRepository logos;

    public BusinessSettingsService(BusinessService businesses, BusinessRepository repository, BusinessLogoRepository logos) {
        this.businesses = businesses; this.repository = repository; this.logos = logos;
    }

    public BusinessSettingsResponse get(UUID businessId) {
        BusinessService.Access access = businesses.access(businessId);
        return response(access);
    }

    @Transactional
    public BusinessSettingsResponse update(UUID businessId, @NotNull @Valid BusinessSettingsRequest request) {
        BusinessService.Access access = owner(businessId);
        access.business().updateSettings(request);
        repository.saveAndFlush(access.business());
        return response(access);
    }

    @Transactional
    public BusinessSettingsResponse replaceLogo(UUID businessId, byte[] content, String contentType) {
        BusinessService.Access access = owner(businessId);
        LogoImages.Normalized image = LogoImages.normalize(content, contentType);
        BusinessLogo logo = logos.saveAndFlush(new BusinessLogo(businessId, image));
        switchLogo(access.business(), logo.getId());
        return response(access);
    }

    @Transactional
    public BusinessSettingsResponse removeLogo(UUID businessId) {
        BusinessService.Access access = owner(businessId);
        switchLogo(access.business(), null);
        return response(access);
    }

    /** Current logo bytes for members of the business; 404 if there is none. */
    public BusinessLogo logo(UUID businessId) {
        Business business = businesses.access(businessId).business();
        return current(business).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void switchLogo(Business business, UUID next) {
        UUID previous = business.getLogoId();
        business.useLogo(next);
        repository.saveAndFlush(business);
        // Logos frozen into issued quotes stay; everything else is removed right away.
        if (previous != null && !previous.equals(next)) logos.deleteIfUnused(previous, business.getId());
    }

    private BusinessService.Access owner(UUID businessId) {
        BusinessService.Access access = businesses.access(businessId);
        if (!access.owner()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return access;
    }

    private BusinessSettingsResponse response(BusinessService.Access access) {
        return BusinessSettingsResponse.from(access.business(), current(access.business()).orElse(null), access.role());
    }

    private Optional<BusinessLogo> current(Business business) {
        return business.getLogoId() == null ? Optional.empty() : logos.findByIdAndBusinessId(business.getLogoId(), business.getId());
    }
}
