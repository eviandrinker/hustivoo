package digital.margraf.hustivoo_backend.membership;

import digital.margraf.hustivoo_backend.user.User;
import digital.margraf.hustivoo_backend.business.Business;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "memberships", uniqueConstraints = @UniqueConstraint(
        name = "uq_memberships_user_business", columnNames = {"user_id", "business_id"}))
public class Membership {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, updatable = false)
    private Business business;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MembershipRole role;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Membership() {}

    public Membership(User user, Business business, MembershipRole role) {
        this.user = Objects.requireNonNull(user);
        this.business = Objects.requireNonNull(business);
        this.role = Objects.requireNonNull(role);
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Business getBusiness() { return business; }
    public MembershipRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
