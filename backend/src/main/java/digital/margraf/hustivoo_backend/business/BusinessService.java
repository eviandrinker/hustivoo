package digital.margraf.hustivoo_backend.business;

import digital.margraf.hustivoo_backend.membership.Membership;
import digital.margraf.hustivoo_backend.membership.MembershipRepository;
import digital.margraf.hustivoo_backend.membership.MembershipRole;
import digital.margraf.hustivoo_backend.user.User;
import digital.margraf.hustivoo_backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@Transactional(readOnly = true)
public class BusinessService {
    private final BusinessRepository businesses;
    private final MembershipRepository memberships;
    private final UserRepository users;

    public BusinessService(BusinessRepository businesses, MembershipRepository memberships, UserRepository users) {
        this.businesses = businesses;
        this.memberships = memberships;
        this.users = users;
    }

    @Transactional
    public BusinessResponse create(@NotNull @Valid CreateBusinessRequest request) {
        User user = currentUser();
        Business business = businesses.save(new Business(request.name(), request.industry(),
                request.legalForm(), request.country(), request.currency()));
        memberships.saveAndFlush(new Membership(user, business, MembershipRole.OWNER));
        return BusinessResponse.from(business);
    }

    public List<BusinessResponse> list() {
        return businesses.findAllForMember(currentUser().getId()).stream().map(BusinessResponse::from).toList();
    }

    public BusinessResponse get(UUID businessId) {
        return businesses.findForMember(businessId, currentUser().getId()).map(BusinessResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** The caller's membership of a business; unknown and foreign businesses are indistinguishable (404). */
    public record Access(Business business, MembershipRole role, UUID userId) {
        public boolean owner() { return role == MembershipRole.OWNER; }
    }

    public Access access(UUID businessId) {
        UUID userId = currentUser().getId();
        Membership membership = memberships.findByBusiness_IdAndUser_Id(businessId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // Unwrap the lazy proxy so package-private mutators always act on the managed entity itself.
        return new Access(org.hibernate.Hibernate.unproxy(membership.getBusiness(), Business.class), membership.getRole(), userId);
    }

    /** Current document sender of a business the caller belongs to (membership is checked). */
    public SenderProfile sender(UUID businessId) {
        return SenderProfile.of(access(businessId).business());
    }

    private User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
