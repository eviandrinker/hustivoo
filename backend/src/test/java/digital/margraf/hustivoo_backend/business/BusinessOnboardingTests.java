package digital.margraf.hustivoo_backend.business;

import digital.margraf.hustivoo_backend.support.PostgresIntegrationTest;
import digital.margraf.hustivoo_backend.user.User;
import digital.margraf.hustivoo_backend.user.UserRepository;
import digital.margraf.hustivoo_backend.membership.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class BusinessOnboardingTests extends PostgresIntegrationTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PASSWORD = "ExamplePassword123!";
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired MembershipRepository memberships;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;

    record Browser(User user, MockHttpSession session, String csrf) {}

    Browser anonymous() throws Exception {
        var result = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        return new Browser(null, (MockHttpSession) result.getRequest().getSession(),
                JSON.readTree(result.getResponse().getContentAsString()).get("token").asText());
    }

    Browser login() throws Exception {
        User user = users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", encoder.encode(PASSWORD)));
        Browser anonymous = anonymous();
        var result = mvc.perform(post("/api/v1/auth/login").session(anonymous.session())
                .header("X-CSRF-TOKEN", anonymous.csrf()).contentType("application/json")
                .content(JSON.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();
        var csrf = mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return new Browser(user, session, JSON.readTree(csrf.getResponse().getContentAsString()).get("token").asText());
    }

    JsonNode create(Browser browser, String body) throws Exception {
        var result = mvc.perform(post("/api/businesses").session(browser.session())
                .header("X-CSRF-TOKEN", browser.csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode response = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/businesses/" + response.get("id").asText());
        return response;
    }

    @Test
    void createsBusinessWithOwnerAndSafeDto() throws Exception {
        Browser owner = login();
        var response = create(owner, """
                {"name":" Studio ","industry":"Design","legalForm":"Einzelfirma","country":"ch","currency":"chf"}
                """);
        assertThat(response.get("name").asText()).isEqualTo("Studio");
        assertThat(response.get("industry").asText()).isEqualTo("Design");
        assertThat(response.get("legalForm").asText()).isEqualTo("Einzelfirma");
        assertThat(response.get("country").asText()).isEqualTo("CH");
        assertThat(response.get("currency").asText()).isEqualTo("CHF");
        assertThat(response.get("createdAt").isNull()).isFalse();
        assertThat(response.has("memberships")).isFalse();
        assertThat(response.has("passwordHash")).isFalse();
        UUID id = UUID.fromString(response.get("id").asText());
        var membership = memberships.findAllByUser_Id(owner.user().getId());
        assertThat(membership).hasSize(1);
        assertThat(membership.getFirst().getRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(jdbc.queryForObject("select business_id from memberships where id = ?", UUID.class,
                membership.getFirst().getId())).isEqualTo(id);
    }

    @Test
    void appliesDefaultsAndAcceptsOtherCountryCurrency() throws Exception {
        Browser owner = login();
        var defaults = create(owner, "{\"name\":\"Default\"}");
        assertThat(defaults.get("country").asText()).isEqualTo("CH");
        assertThat(defaults.get("currency").asText()).isEqualTo("CHF");
        var other = create(owner, "{\"name\":\"Other\",\"country\":\"de\",\"currency\":\"eur\"}");
        assertThat(other.get("country").asText()).isEqualTo("DE");
        assertThat(other.get("currency").asText()).isEqualTo("EUR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\" \"}", "{\"name\":\"Studio\",\"country\":\"ZZ\"}",
            "{\"name\":\"Studio\",\"currency\":\"ZZZ\"}", "{\"name\":\"Studio\",\"country\":\"\"}", "not-json"})
    void rejectsInvalidInputWithoutWrites(String body) throws Exception {
        Browser owner = login();
        long count = businesses.count();
        mvc.perform(post("/api/businesses").session(owner.session()).header("X-CSRF-TOKEN", owner.csrf())
                .contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(content().json("{\"error\":\"Invalid request\"}"));
        assertThat(businesses.count()).isEqualTo(count);
        assertThat(memberships.findAllByUser_Id(owner.user().getId())).isEmpty();
    }

    @Test
    void rejectsOversizedFieldsAndMalformedId() throws Exception {
        Browser owner = login();
        for (String field : java.util.List.of("name", "industry", "legalForm")) {
            var body = new java.util.HashMap<String, String>();
            body.put("name", "Studio"); body.put(field, "a".repeat(256));
            mvc.perform(post("/api/businesses").session(owner.session()).header("X-CSRF-TOKEN", owner.csrf())
                    .contentType("application/json").content(JSON.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/businesses/not-a-uuid").session(owner.session())).andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        Browser anonymous = anonymous();
        mvc.perform(get("/api/businesses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/businesses/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/businesses").session(anonymous.session()).header("X-CSRF-TOKEN", anonymous.csrf())
                .contentType("application/json").content("{\"name\":\"Studio\"}")).andExpect(status().isUnauthorized());
        Browser owner = login();
        mvc.perform(post("/api/businesses").session(owner.session()).contentType("application/json")
                .content("{\"name\":\"Studio\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/businesses").session(owner.session()).header("X-CSRF-TOKEN", "invalid")
                .contentType("application/json").content("{\"name\":\"Studio\"}")).andExpect(status().isForbidden());
        assertThat(memberships.findAllByUser_Id(owner.user().getId())).isEmpty();
    }

    @Test
    void scopesListsAndDetailsToMembershipAndIgnoresClientIdentity() throws Exception {
        Browser alice = login(); Browser bob = login(); Browser outsider = login();
        var first = create(alice, "{\"name\":\"Alice\"}");
        create(alice, "{\"name\":\"Alice second\"}");
        var other = create(bob, "{\"name\":\"Bob\"}");
        String aliceId = first.get("id").asText();
        mvc.perform(get("/api/businesses").session(alice.session()).param("userId", bob.user().getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alice"));
        mvc.perform(get("/api/businesses/" + aliceId).session(alice.session())).andExpect(status().isOk());
        mvc.perform(get("/api/businesses/" + other.get("id").asText()).session(alice.session())
                .header("X-User-Id", bob.user().getId().toString())).andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"Business not found\"}"));
        mvc.perform(get("/api/businesses/" + UUID.randomUUID()).session(alice.session()))
                .andExpect(status().isNotFound()).andExpect(content().json("{\"error\":\"Business not found\"}"));
        mvc.perform(get("/api/businesses").session(outsider.session())).andExpect(content().json("[]"));
        // MEMBER is sufficient to read, not just OWNER.
        memberships.saveAndFlush(new Membership(bob.user(), businesses.findById(UUID.fromString(aliceId)).orElseThrow(), MembershipRole.MEMBER));
        mvc.perform(get("/api/businesses/" + aliceId).session(bob.session())).andExpect(status().isOk());
        // Creating while supplying another user's ID cannot assign ownership to them.
        create(outsider, JSON.writeValueAsString(Map.of("name", "Own", "userId", bob.user().getId(), "role", "MEMBER")));
        assertThat(memberships.findAllByUser_Id(outsider.user().getId())).singleElement()
                .extracting(Membership::getRole).isEqualTo(MembershipRole.OWNER);
    }

    @Test
    void v2PreservesExistingV1Businesses() {
        String schema = "onboarding_upgrade_test";
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema)
                    .defaultSchema(schema).target("1").load().migrate();
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO onboarding_upgrade_test.businesses (id, name, currency) VALUES (?, 'Existing', 'EUR')", id);
            var flyway = org.flywaydb.core.Flyway.configure().dataSource(dataSource)
                    .schemas(schema).defaultSchema(schema).load();
            flyway.migrate();
            flyway.validate();
            var row = jdbc.queryForMap("SELECT name, country, currency, industry, legal_form FROM onboarding_upgrade_test.businesses WHERE id = ?", id);
            assertThat(row).containsEntry("name", "Existing").containsEntry("country", "CH")
                    .containsEntry("currency", "EUR").containsEntry("industry", null).containsEntry("legal_form", null);
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS onboarding_upgrade_test CASCADE");
        }
    }

    @Test
    void rollsBackBusinessWhenMembershipInsertFails() throws Exception {
        Browser owner = login();
        long before = businesses.count();
        // Inject a real database failure in the disposable test database only.
        jdbc.execute("""
                CREATE FUNCTION reject_test_membership() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'test membership failure' USING ERRCODE = '23514'; END $$
                """);
        jdbc.execute("CREATE TRIGGER reject_test_membership BEFORE INSERT ON memberships FOR EACH ROW EXECUTE FUNCTION reject_test_membership()");
        try {
            mvc.perform(post("/api/businesses").session(owner.session()).header("X-CSRF-TOKEN", owner.csrf())
                    .contentType("application/json").content("{\"name\":\"Must roll back\"}"))
                    .andExpect(status().isInternalServerError()).andExpect(content().json("{\"error\":\"Unable to complete request\"}"));
            assertThat(businesses.count()).isEqualTo(before);
            assertThat(memberships.findAllByUser_Id(owner.user().getId())).isEmpty();
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_membership ON memberships");
            jdbc.execute("DROP FUNCTION reject_test_membership()");
        }
    }
}
