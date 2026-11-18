package digital.margraf.hustivoo_backend.customer;

import digital.margraf.hustivoo_backend.user.User;
import digital.margraf.hustivoo_backend.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.UUID;

@Component
public class CustomerHistory {
    private final CustomerActivityRepository activities;
    private final UserRepository users;
    public CustomerHistory(CustomerActivityRepository activities, UserRepository users) { this.activities = activities; this.users = users; }
    User actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByEmail(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
    void record(UUID businessId, UUID customerId, String event) {
        User actor = actor();
        activities.save(new CustomerActivity(businessId, customerId, event, actor.getId(), actor.getEmail()));
    }
}
