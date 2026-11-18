package digital.margraf.hustivoo_backend;

import digital.margraf.hustivoo_backend.business.*;
import digital.margraf.hustivoo_backend.membership.*;
import digital.margraf.hustivoo_backend.user.*;
import digital.margraf.hustivoo_backend.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Transactional
class DomainPersistenceTests extends PostgresIntegrationTest {
    // Pre-encoded bcrypt fixture; no plaintext password is persisted.
    private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired UserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired MembershipRepository memberships;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void emailIsCanonicalAndUnique() {
        User user = users.saveAndFlush(new User(" Alice@Example.com ", HASH));
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThatThrownBy(() -> users.saveAndFlush(new User("alice@example.com", HASH)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void businessPersistsAndUpdatesTimestamps() {
        Business business = businesses.saveAndFlush(new Business("Studio"));
        UUID id = business.getId();
        var createdAt = business.getCreatedAt();
        var updatedAt = business.getUpdatedAt();
        entityManager.clear();
        Business loaded = businesses.findById(id).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Studio");
        assertThat(loaded.getCurrency()).isEqualTo("CHF");
        loaded.rename("New Studio");
        businesses.flush();
        assertThat(loaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(loaded.getUpdatedAt()).isAfter(updatedAt);
        entityManager.clear();
        assertThat(businesses.findById(id).orElseThrow().getName()).isEqualTo("New Studio");
    }

    @Test
    void membershipSupportsMultipleBusinessesAndMembers() {
        User owner = users.save(new User("owner@example.com", HASH));
        User member = users.save(new User("member@example.com", HASH));
        Business first = businesses.save(new Business("First"));
        Business second = businesses.save(new Business("Second", "EUR"));
        Membership membership = memberships.save(new Membership(owner, first, MembershipRole.OWNER));
        memberships.save(new Membership(owner, second, MembershipRole.MEMBER));
        memberships.saveAndFlush(new Membership(member, first, MembershipRole.MEMBER));
        UUID id = membership.getId();
        entityManager.clear();
        Membership loaded = memberships.findById(id).orElseThrow();
        assertThat(loaded.getUser().getEmail()).isEqualTo("owner@example.com");
        assertThat(loaded.getBusiness().getName()).isEqualTo("First");
        assertThat(loaded.getRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(memberships.findAllByUser_Id(owner.getId())).hasSize(2);
        assertThat(memberships.findAllByBusiness_Id(first.getId())).hasSize(2);
    }

    @Test
    void duplicateMembershipIsRejected() {
        User user = users.save(new User("member@example.com", HASH));
        Business business = businesses.save(new Business("Studio"));
        memberships.saveAndFlush(new Membership(user, business, MembershipRole.OWNER));
        assertThatThrownBy(() -> memberships.saveAndFlush(new Membership(user, business, MembershipRole.MEMBER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "business"})
    void missingMembershipParentIsRejected(String missing) {
        UUID userId = users.saveAndFlush(new User("member@example.com", HASH)).getId();
        UUID businessId = businesses.saveAndFlush(new Business("Studio")).getId();
        UUID referencedUser = missing.equals("user") ? UUID.randomUUID() : userId;
        UUID referencedBusiness = missing.equals("business") ? UUID.randomUUID() : businessId;
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO memberships (id, user_id, business_id, role) VALUES (?, ?, ?, 'MEMBER')",
                UUID.randomUUID(), referencedUser, referencedBusiness))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"users", "businesses"})
    void deletingReferencedParentIsRejected(String table) {
        User user = users.save(new User("member@example.com", HASH));
        Business business = businesses.save(new Business("Studio"));
        memberships.saveAndFlush(new Membership(user, business, MembershipRole.OWNER));
        assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table + " WHERE id = ?",
                table.equals("users") ? user.getId() : business.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidRoleIsRejectedByDatabase() {
        User user = users.saveAndFlush(new User("member@example.com", HASH));
        Business business = businesses.saveAndFlush(new Business("Studio"));
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO memberships (id, user_id, business_id, role) VALUES (?, ?, ?, 'ADMIN')",
                UUID.randomUUID(), user.getId(), business.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseDefaultsAndFlywaySchemaAreCompatible() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO businesses (id, name) VALUES (?, 'Default currency')", id);
        Business business = businesses.findById(id).orElseThrow();
        assertThat(business.getCurrency()).isEqualTo("CHF");
        assertThat(business.getCreatedAt()).isNotNull();
        assertThat(business.getUpdatedAt()).isNotNull();
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("2");
    }
}
