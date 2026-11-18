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
class CustomerCrmTests extends PostgresIntegrationTest {
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
    String newBusiness(Browser browser) throws Exception { return create(browser, "{\"name\":\"CRM\"}").get("id").asText(); }
    String contactBody(String first, boolean primary) { return JSON.writeValueAsString(Map.of("firstName", first, "lastName", "Muster", "primaryContact", primary)); }
    JsonNode write(Browser browser, String method, String path, String body, int expected) throws Exception {
        var result = mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method), path).session(browser.session())
                .header("X-CSRF-TOKEN", browser.csrf()).contentType("application/json").content(body))
                .andExpect(status().is(expected)).andReturn();
        return expected == 204 ? null : JSON.readTree(result.getResponse().getContentAsString());
    }
    @Test
    void numbersAreConcurrentPerBusinessAndRollbackSafe() throws Exception {
        Browser owner = login(); String business = newBusiness(owner);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks = java.util.stream.IntStream.range(0, 8).mapToObj(i -> (java.util.concurrent.Callable<String>) () ->
                    write(owner, "POST", base(business), payload, 201).get("customerNumber").asText()).toList();
            var results = executor.invokeAll(tasks);
            var numbers = new java.util.ArrayList<String>();
            for (var result : results) numbers.add(result.get());
            assertThat(numbers).containsExactlyInAnyOrder("K-0001","K-0002","K-0003","K-0004","K-0005","K-0006","K-0007","K-0008");
        }
        assertThat(write(owner, "POST", base(newBusiness(owner)), payload, 201).get("customerNumber").asText()).isEqualTo("K-0001");
        write(owner, "POST", base(business), "{}", 400);
        assertThat(write(owner, "POST", base(business), payload, 201).get("customerNumber").asText()).isEqualTo("K-0009");
    }
    @Test
    void statusFiltersSortAndImmutableHistory() throws Exception {
        Browser owner = login(); String business = newBusiness(owner); String id = addCustomer(owner, business);
        String path = base(business) + "/" + id;
        write(owner, "PATCH", path + "/status", "{\"status\":\"LEAD\"}", 200);
        mvc.perform(get(base(business)).session(owner.session()).param("status", "LEAD").param("type", "COMPANY").param("search", "K-0001"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(base(business)).session(owner.session()).param("status", "INACTIVE")).andExpect(content().json("[]"));
        String second = addCustomer(owner, business);
        mvc.perform(get(base(business)).session(owner.session()).param("sort", "CREATED_DESC")).andExpect(jsonPath("$[0].id").value(second));
        write(owner, "PUT", path, payload.replace("Studio Test", "Changed"), 200);
        write(owner, "PATCH", path + "/archive", "{}", 200); write(owner, "PATCH", path + "/restore", "{}", 200);
        var result = mvc.perform(get(path + "/activities").session(owner.session())).andExpect(status().isOk()).andReturn();
        var activities = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(activities.size()).isEqualTo(5);
        assertThat(activities.get(0).get("event").asText()).isEqualTo("RESTORED");
        assertThat(activities.get(4).get("event").asText()).isEqualTo("CREATED");
        write(owner, "POST", path + "/activities", "{\"event\":\"CREATED\"}", 405);
    }
    @Test
    void contactsEnforceOnePrimaryAndCustomerType() throws Exception {
        Browser owner = login(); String business = newBusiness(owner); String id = addCustomer(owner, business);
        String path = base(business) + "/" + id;
        String first = write(owner, "POST", path + "/contacts", contactBody("Anna", true), 201).get("id").asText();
        String second = write(owner, "POST", path + "/contacts", contactBody("Beat", true), 201).get("id").asText();
        assertThat(jdbc.queryForObject("select count(*) from customer_contacts where customer_id = ? and primary_contact", Integer.class, UUID.fromString(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select primary_contact from customer_contacts where id = ?", Boolean.class, UUID.fromString(first))).isFalse();
        write(owner, "PUT", path + "/contacts/" + first, contactBody("Anna neu", true), 200);
        write(owner, "PUT", path, payload.replace("COMPANY", "PRIVATE"), 409);
        write(owner, "DELETE", path + "/contacts/" + second, "", 204);
        write(owner, "DELETE", path + "/contacts/" + first, "", 204);
        write(owner, "PUT", path, payload.replace("COMPANY", "PRIVATE"), 200);
        write(owner, "POST", path + "/contacts", contactBody("Anna", true), 409);
    }
    @Test
    void concurrentPrimaryContactsNeverLeaveTwoPrimaries() throws Exception {
        Browser owner = login(); String business = newBusiness(owner); String id = addCustomer(owner, business);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(java.util.List.of(
                    (java.util.concurrent.Callable<JsonNode>) () -> write(owner, "POST", base(business)+"/"+id+"/contacts", contactBody("Anna", true), 201),
                    () -> write(owner, "POST", base(business)+"/"+id+"/contacts", contactBody("Beat", true), 201)));
            for (var result : results) result.get();
        }
        assertThat(jdbc.queryForObject("select count(*) from customer_contacts where customer_id = ? and primary_contact", Integer.class, UUID.fromString(id))).isEqualTo(1);
    }
    @Test
    void notesAttributeActorAndCrmChildrenCannotCrossTenantsOrCustomers() throws Exception {
        Browser owner = login(); Browser stranger = login(); String business = newBusiness(owner); String id = addCustomer(owner, business);
        String otherBusiness = newBusiness(owner); String otherCustomer = addCustomer(owner, business);
        String path = base(business)+"/"+id;
        var note = write(owner, "POST", path+"/notes", "{\"content\":\"Interne Notiz\",\"createdBy\":\"forged\"}", 201);
        assertThat(note.get("createdBy").asText()).isEqualTo(owner.user().getId().toString());
        String contact = write(owner, "POST", path+"/contacts", contactBody("Anna", true), 201).get("id").asText();
        for (String child : java.util.List.of("notes", "activities", "contacts")) {
            mvc.perform(get(path+"/"+child).session(stranger.session())).andExpect(status().isNotFound());
            mvc.perform(get(base(otherBusiness)+"/"+id+"/"+child).session(owner.session())).andExpect(status().isNotFound());
        }
        write(owner, "PUT", base(business)+"/"+otherCustomer+"/contacts/"+contact, contactBody("Attack", true), 404);
        write(owner, "DELETE", base(business)+"/"+otherCustomer+"/contacts/"+contact, "", 404);
        write(stranger, "POST", path+"/notes", "{\"content\":\"Attack\"}", 404);
        write(owner, "POST", base(otherBusiness)+"/"+id+"/notes", "{\"content\":\"Attack\"}", 404);
        mvc.perform(get(base(business)+"/"+otherCustomer+"/notes").session(owner.session())).andExpect(content().json("[]"));
    }
    @Test
    void crmValidationAndCsrf() throws Exception {
        Browser owner = login(); String business = newBusiness(owner); String id = addCustomer(owner, business); String path = base(business)+"/"+id;
        write(owner, "POST", path+"/contacts", "{}", 400); write(owner, "POST", path+"/notes", "{\"content\":\" \"}", 400);
        write(owner, "PATCH", path+"/status", "{\"status\":\"OTHER\"}", 400);
        String contact = write(owner, "POST", path+"/contacts", contactBody("Anna", false), 201).get("id").asText();
        for (String child : java.util.List.of("contacts", "notes")) {
            mvc.perform(post(path+"/"+child).session(owner.session()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
            mvc.perform(get(path+"/"+child)).andExpect(status().isUnauthorized());
        }
        mvc.perform(patch(path+"/status").session(owner.session()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put(path+"/contacts/"+contact).session(owner.session()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete(path+"/contacts/"+contact).session(owner.session())).andExpect(status().isForbidden());
    }
    @Test
    void migrationBackfillsCustomersAndCounters() {
        String schema = "crm_upgrade_test";
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("3").load().migrate();
            UUID business = UUID.randomUUID(); UUID id = UUID.randomUUID();
            jdbc.update("insert into crm_upgrade_test.businesses(id,name) values (?, 'Legacy')", business);
            jdbc.update("insert into crm_upgrade_test.customers(id,business_id,type,display_name,notes) values (?,?,'COMPANY','Legacy','Old note')", id,business);
            var flyway = org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();
            flyway.migrate(); flyway.validate();
            var row = jdbc.queryForMap("select customer_number,status from crm_upgrade_test.customers where id=?", id);
            assertThat(row).containsEntry("customer_number","K-0001").containsEntry("status","ACTIVE");
            assertThat(jdbc.queryForObject("select last_value from crm_upgrade_test.customer_number_counters where business_id=?", Long.class,business)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select content from crm_upgrade_test.customer_notes where customer_id=?",String.class,id)).isEqualTo("Old note");
            assertThat(jdbc.queryForObject("select event from crm_upgrade_test.customer_activities where customer_id=?",String.class,id)).isEqualTo("CREATED");
        } finally { jdbc.execute("DROP SCHEMA IF EXISTS crm_upgrade_test CASCADE"); }
    }
}
