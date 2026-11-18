package digital.margraf.hustivoo_backend.customer;

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
class CustomerTests extends PostgresIntegrationTest {
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

    String base(String business) { return "/api/businesses/" + business + "/customers"; }
    final String payload = "{\"type\":\"COMPANY\",\"displayName\":\"Studio Test\",\"companyName\":\"Studio Test\",\"email\":\"hello@example.com\"}";
    String addCustomer(Browser browser, String business) throws Exception {
        var result = mvc.perform(post(base(business)).session(browser.session()).header("X-CSRF-TOKEN", browser.csrf())
                .contentType("application/json").content(payload)).andExpect(status().isCreated()).andReturn();
        var response = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("businessId").asText()).isEqualTo(business);
        assertThat(response.get("country").asText()).isEqualTo("CH");
        return response.get("id").asText();
    }
    @Test
    void lifecycleAndSearch() throws Exception {
        Browser owner = login();
        String business = create(owner, "{\"name\":\"Studio\"}").get("id").asText();
        String id = addCustomer(owner, business);
        mvc.perform(get(base(business) + "/" + id).session(owner.session())).andExpect(status().isOk());
        for (String search : java.util.List.of("STUDIO", "hello@example.com")) {
            mvc.perform(get(base(business)).param("search", search).session(owner.session()))
                    .andExpect(jsonPath("$.length()").value(1));
        }
        mvc.perform(get(base(business)).param("search", "%").session(owner.session())).andExpect(content().json("[]"));
        mvc.perform(put(base(business) + "/" + id).session(owner.session()).header("X-CSRF-TOKEN", owner.csrf())
                .contentType("application/json").content(payload.replace("Studio Test", "Updated")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Updated"));
        mvc.perform(patch(base(business) + "/" + id + "/archive").session(owner.session()).header("X-CSRF-TOKEN", owner.csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        mvc.perform(get(base(business)).session(owner.session())).andExpect(content().json("[]"));
        mvc.perform(get(base(business)).param("includeArchived", "true").session(owner.session())).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(patch(base(business) + "/" + id + "/restore").session(owner.session()).header("X-CSRF-TOKEN", owner.csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
        mvc.perform(get(base(business)).session(owner.session())).andExpect(jsonPath("$.length()").value(1));
    }
    @Test
    void membershipAndCustomerIdsAreAlwaysScoped() throws Exception {
        Browser alice = login(); Browser bob = login();
        String a = create(alice, "{\"name\":\"A\"}").get("id").asText();
        String a2 = create(alice, "{\"name\":\"A2\"}").get("id").asText();
        String b = create(bob, "{\"name\":\"B\"}").get("id").asText();
        String id = addCustomer(alice, a);
        for (String scope : java.util.List.of(a2, b, UUID.randomUUID().toString())) {
            String path = base(scope) + "/" + id;
            mvc.perform(get(path).session(alice.session())).andExpect(status().isNotFound());
            mvc.perform(put(path).session(alice.session()).header("X-CSRF-TOKEN", alice.csrf()).contentType("application/json").content(payload)).andExpect(status().isNotFound());
            for (String operation : java.util.List.of("archive", "restore")) {
                mvc.perform(patch(path + "/" + operation).session(alice.session()).header("X-CSRF-TOKEN", alice.csrf())).andExpect(status().isNotFound());
            }
        }
        mvc.perform(get(base(a)).session(bob.session())).andExpect(status().isNotFound());
        mvc.perform(post(base(a)).session(bob.session()).header("X-CSRF-TOKEN", bob.csrf()).contentType("application/json").content(payload)).andExpect(status().isNotFound());
        mvc.perform(get(base(a) + "/" + UUID.randomUUID()).session(alice.session())).andExpect(status().isNotFound());
        memberships.saveAndFlush(new Membership(bob.user(), businesses.findById(UUID.fromString(a)).orElseThrow(), MembershipRole.MEMBER));
        addCustomer(bob, a); // Explicitly permit MEMBER writes.
    }
    @Test
    void authenticationCsrfAndValidation() throws Exception {
        Browser owner = login();
        String business = create(owner, "{\"name\":\"Studio\"}").get("id").asText();
        String id = addCustomer(owner, business);
        mvc.perform(get(base(business))).andExpect(status().isUnauthorized());
        Browser anonymous = anonymous();
        mvc.perform(post(base(business)).session(anonymous.session()).header("X-CSRF-TOKEN", anonymous.csrf()).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
        mvc.perform(post(base(business)).session(owner.session()).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        mvc.perform(put(base(business) + "/" + id).session(owner.session()).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        for (String operation : java.util.List.of("archive", "restore")) {
            mvc.perform(patch(base(business) + "/" + id + "/" + operation).session(owner.session())).andExpect(status().isForbidden());
        }
        for (String body : java.util.List.of("{}", payload.replace("COMPANY", "UNKNOWN"), payload.replace("hello@example.com", "invalid"), payload.replace("Studio Test", " "), payload.replace("Studio Test", "x".repeat(256)))) {
            mvc.perform(post(base(business)).session(owner.session()).header("X-CSRF-TOKEN", owner.csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get(base(business)).param("search", "x".repeat(321)).session(owner.session())).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from customers where business_id = ?", Integer.class, UUID.fromString(business))).isEqualTo(1);
    }
}
