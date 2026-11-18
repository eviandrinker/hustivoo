package digital.margraf.hustivoo_backend.servicecatalog;

import digital.margraf.hustivoo_backend.business.*;

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
class ServiceCatalogTests extends PostgresIntegrationTest {
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

    String base(String business) { return "/api/businesses/" + business + "/services"; }
    final String payload = """
            {"name":"Beratung","description":"Strategie und Planung","category":"Consulting",
             "unit":"HOUR","unitPrice":"95.10","taxRate":"8.10"}
            """;
    String business(Browser browser) throws Exception { return create(browser, "{\"name\":\"Studio\"}").get("id").asText(); }
    JsonNode add(Browser browser, String business, String body) throws Exception {
        var result = mvc.perform(post(base(business)).session(browser.session()).header("X-CSRF-TOKEN", browser.csrf())
                .contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn();
        var response = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(base(business) + "/" + response.get("id").asText());
        assertThat(response.get("businessId").asText()).isEqualTo(business);
        return response;
    }
    @Test void lifecycleKeepsStableIdsAndExactAmounts() throws Exception {
        Browser owner = login(); String business = business(owner);
        JsonNode service = add(owner, business, payload);
        String id = service.get("id").asText(); String path = base(business) + "/" + id;
        assertThat(service.get("currency").asText()).isEqualTo("CHF");
        assertThat(service.get("unitPrice").asText()).isEqualTo("95.10");
        assertThat(service.get("taxRate").asText()).isEqualTo("8.10");
        mvc.perform(get(path).session(owner.session())).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(put(path).session(owner.session()).header("X-CSRF-TOKEN", owner.csrf()).contentType("application/json")
                .content(payload.replace("95.10", "99999999999999999.99")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unitPrice").value("99999999999999999.99"));
        assertThat(jdbc.queryForObject("select unit_price from services where id = ?", java.math.BigDecimal.class, UUID.fromString(id)))
                .isEqualByComparingTo("99999999999999999.99");
        mvc.perform(patch(path + "/archive").session(owner.session()).header("X-CSRF-TOKEN", owner.csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get(base(business)).session(owner.session())).andExpect(content().json("[]"));
        mvc.perform(get(base(business)).param("active", "false").session(owner.session())).andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(get(path).session(owner.session())).andExpect(status().isOk()); // Archived remains referenceable.
        mvc.perform(patch(path + "/restore").session(owner.session()).header("X-CSRF-TOKEN", owner.csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.active").value(true));
    }
    @Test void searchCategoriesAndSortingAreScoped() throws Exception {
        Browser owner = login(); String business = business(owner);
        add(owner, business, payload);
        add(owner, business, payload.replace("Beratung", "Analyse").replace("Consulting", "Design").replace("95.10", "120.00"));
        for (String search : java.util.List.of("BERAT", "planung")) {
            mvc.perform(get(base(business)).param("search", search).session(owner.session()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(search.equals("planung") ? 2 : 1));
        }
        mvc.perform(get(base(business)).param("search", "%_").session(owner.session())).andExpect(content().json("[]"));
        mvc.perform(get(base(business)).param("category", "consulting").session(owner.session())).andExpect(jsonPath("$.length()").value(1));
        for (String sort : java.util.List.of("NAME_ASC", "PRICE_DESC")) {
            mvc.perform(get(base(business)).param("sort", sort).session(owner.session())).andExpect(jsonPath("$[0].name").value("Analyse"));
        }
        for (String sort : java.util.List.of("NAME_DESC", "PRICE_ASC")) {
            mvc.perform(get(base(business)).param("sort", sort).session(owner.session())).andExpect(jsonPath("$[0].name").value("Beratung"));
        }
        mvc.perform(get(base(business)).param("category", "Missing").session(owner.session())).andExpect(content().json("[]"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1.001", "100000000000000000.00", "NaN"})
    void invalidPricesAreRejected(String price) throws Exception {
        Browser owner = login(); String business = business(owner);
        mvc.perform(post(base(business)).session(owner.session()).header("X-CSRF-TOKEN", owner.csrf())
                .contentType("application/json").content(payload.replace("95.10", price))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from services where business_id = ?", Integer.class, UUID.fromString(business))).isZero();
    }
    @Test void validationAndDefaults() throws Exception {
        Browser owner = login(); String business = business(owner);
        for (String body : java.util.List.of("{}", payload.replace("Beratung", " "), payload.replace("Beratung", "x".repeat(256)),
                payload.replace("HOUR", "INVALID"), payload.replace("8.10", "100.01"), payload.replace("8.10", "-1"),
                payload.replace("8.10", "1.111"), payload.replace("Consulting", "x".repeat(121)),
                payload.replace("Strategie und Planung", "x".repeat(4001)), payload.replace("\"unit\":", "\"currency\":\"ZZZ\",\"unit\":"))) {
            mvc.perform(post(base(business)).session(owner.session()).header("X-CSRF-TOKEN", owner.csrf()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid request"));
        }
        var result = add(owner, business, "{\"name\":\" Kostenlos \",\"unit\":\"FIXED\",\"unitPrice\":\"0\"}");
        assertThat(result.get("unitPrice").asText()).isEqualTo("0.00");
        assertThat(result.get("taxRate").isNull()).isTrue();
        assertThat(result.get("name").asText()).isEqualTo("Kostenlos");
        for (var param : Map.of("search", "x".repeat(321), "category", "x".repeat(121), "sort", "INVALID", "active", "invalid").entrySet()) {
            mvc.perform(get(base(business)).param(param.getKey(), param.getValue()).session(owner.session())).andExpect(status().isBadRequest());
        }
    }
    @Test void membershipAndIdorUseIdenticalNotFoundResponses() throws Exception {
        Browser alice = login(); Browser bob = login();
        String a = business(alice); String a2 = business(alice); String b = business(bob);
        String id = add(alice, a, payload).get("id").asText();
        String expected = mvc.perform(get(base(a) + "/" + UUID.randomUUID()).session(alice.session()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        for (String scope : java.util.List.of(a2, b, UUID.randomUUID().toString())) {
            String path = base(scope) + "/" + id;
            mvc.perform(get(path).session(alice.session())).andExpect(status().isNotFound()).andExpect(content().string(expected));
            mvc.perform(put(path).session(alice.session()).header("X-CSRF-TOKEN", alice.csrf()).contentType("application/json").content(payload))
                    .andExpect(status().isNotFound()).andExpect(content().string(expected));
            for (String operation : java.util.List.of("archive", "restore")) {
                mvc.perform(patch(path + "/" + operation).session(alice.session()).header("X-CSRF-TOKEN", alice.csrf()))
                        .andExpect(status().isNotFound()).andExpect(content().string(expected));
            }
        }
        mvc.perform(get(base(a)).session(bob.session())).andExpect(status().isNotFound()).andExpect(content().string(expected));
        mvc.perform(post(base(a)).session(bob.session()).header("X-CSRF-TOKEN", bob.csrf()).contentType("application/json").content(payload))
                .andExpect(status().isNotFound()).andExpect(content().string(expected));
        memberships.saveAndFlush(new Membership(bob.user(), businesses.findById(UUID.fromString(a)).orElseThrow(), MembershipRole.MEMBER));
        add(bob, a, payload);
    }
    @Test void sessionAndCsrfAreRequired() throws Exception {
        Browser owner = login(); String business = business(owner);
        String path = base(business) + "/" + add(owner, business, payload).get("id").asText();
        mvc.perform(get(base(business))).andExpect(status().isUnauthorized());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        Browser anonymous = anonymous();
        mvc.perform(post(base(business)).session(anonymous.session()).header("X-CSRF-TOKEN", anonymous.csrf()).contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(base(business)).session(owner.session()).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(put(path).session(owner.session()).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        for (String operation : java.util.List.of("archive", "restore")) {
            mvc.perform(patch(path + "/" + operation).session(owner.session())).andExpect(status().isForbidden());
        }
    }
    @Test void upgradeFromV4PreservesDataAndAddsConstraints() {
        String schema = "catalog_upgrade_test";
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("4").load().migrate();
            UUID business = UUID.randomUUID(); UUID id = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".businesses (id,name) values (?, 'Existing')", business);
            var flyway = org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("5").load();
            flyway.migrate(); flyway.validate();
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("5");
            assertThat(jdbc.queryForObject("select name from " + schema + ".businesses where id = ?", String.class, business)).isEqualTo("Existing");
            String insert = "insert into " + schema + ".services (id,business_id,name,unit,unit_price) values (?, ?, 'Test', 'DAY', ?)";
            jdbc.update(insert, id, business, new java.math.BigDecimal("12345678901234567.89"));
            assertThat(jdbc.queryForObject("select unit_price from " + schema + ".services where id = ?", java.math.BigDecimal.class, id))
                    .isEqualByComparingTo("12345678901234567.89");
            assertThat(jdbc.queryForObject("select currency from " + schema + ".services where id = ?", String.class, id)).isEqualTo("CHF");
            assertThat(jdbc.queryForObject("select active from " + schema + ".services where id = ?", Boolean.class, id)).isTrue();
            assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), UUID.randomUUID(), java.math.BigDecimal.ONE))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), business, new java.math.BigDecimal("-1")))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }
}
