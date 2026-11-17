package digital.margraf.hustivoo_backend.user;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    /**
     * Accepts an already encoded password hash from a trusted password encoder.
     * Never pass a plaintext password. Authentication and encoding are not part of this entity.
     */
    public User(String email, String passwordHash) {
        this.email = normalizeEmail(email);
        this.passwordHash = java.util.Objects.requireNonNull(passwordHash);
        if (this.email.isBlank() || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Email and encoded password hash are required");
        }
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPasswordHash() { return passwordHash; }

    public static String normalizeEmail(String email) {
        return java.util.Objects.requireNonNull(email).strip().toLowerCase(java.util.Locale.ROOT);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
