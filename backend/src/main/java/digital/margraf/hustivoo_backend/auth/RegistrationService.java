package digital.margraf.hustivoo_backend.auth;

import digital.margraf.hustivoo_backend.user.User;
import digital.margraf.hustivoo_backend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public RegistrationService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public AuthController.UserResponse register(String email, String password) {
        // The database unique constraint also handles concurrent registrations.
        return AuthController.UserResponse.from(users.saveAndFlush(new User(email, encoder.encode(password))));
    }
}
