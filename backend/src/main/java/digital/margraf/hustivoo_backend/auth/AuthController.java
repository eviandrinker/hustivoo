package digital.margraf.hustivoo_backend.auth;

import digital.margraf.hustivoo_backend.user.User;
import digital.margraf.hustivoo_backend.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegistrationService registration;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessions;
    private final HttpSessionSecurityContextRepository contexts;
    private final UserRepository users;

    public AuthController(RegistrationService registration, AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessions, HttpSessionSecurityContextRepository contexts, UserRepository users) {
        this.registration = registration;
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
        this.contexts = contexts;
        this.users = users;
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 72) String password) {
        public RegisterRequest { if (email != null) email = User.normalizeEmail(email); }
        @Override public String toString() { return "RegisterRequest[redacted]"; }
    }
    public record LoginRequest(@NotBlank @Size(max = 320) String email,
            @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "LoginRequest[redacted]"; }
    }
    public record UserResponse(UUID id, String email) {
        static UserResponse from(User user) { return new UserResponse(user.getId(), user.getEmail()); }
    }
    public record CsrfResponse(String headerName, String token) {
        @Override public String toString() { return "CsrfResponse[redacted]"; }
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        checkPasswordBytes(request.password());
        return registration.register(request.email(), request.password());
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        checkPasswordBytes(body.password());
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(User.normalizeEmail(body.email()), body.password()));
        sessions.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return currentUser(authentication);
    }

    @GetMapping("/me")
    public UserResponse currentUser(Authentication authentication) {
        return users.findByEmail(authentication.getName()).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private static void checkPasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request");
        }
    }
}
