package digital.margraf.hustivoo_backend.quote;

import digital.margraf.hustivoo_backend.business.BusinessRepository;
import digital.margraf.hustivoo_backend.membership.Membership;
import digital.margraf.hustivoo_backend.membership.MembershipRepository;
import digital.margraf.hustivoo_backend.membership.MembershipRole;
import digital.margraf.hustivoo_backend.support.PostgresIntegrationTest;
import digital.margraf.hustivoo_backend.user.User;
import digital.margraf.hustivoo_backend.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class QuoteTests extends PostgresIntegrationTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PASSWORD = "ExamplePassword123!";
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired MembershipRepository memberships;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired QuoteService quoteService;
    @Autowired javax.sql.DataSource dataSource;

    record Browser(User user, MockHttpSession session, String csrf) {}
    record Tenant(Browser owner, String business, String customer, String service) {}

    // ---------------------------------------------------------------- fixtures

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

    MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request, Browser browser) {
        return request.session(browser.session()).header("X-CSRF-TOKEN", browser.csrf()).contentType("application/json");
    }

    JsonNode created(Browser browser, String path, String body) throws Exception {
        var result = mvc.perform(write(post(path), browser).content(body)).andExpect(status().isCreated()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return JSON.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    String business(Browser browser, String name) throws Exception {
        return created(browser, "/api/businesses", "{\"name\":\"" + name + "\",\"currency\":\"CHF\"}").get("id").asText();
    }

    String customer(Browser browser, String business, String name) throws Exception {
        return created(browser, "/api/businesses/" + business + "/customers", """
                {"type":"COMPANY","displayName":"%s","companyName":"%s AG","firstName":"Erika","lastName":"Muster",
                 "email":"erika@example.com","street":"Bahnhofstrasse 1","postalCode":"8001","city":"Zürich","country":"CH"}
                """.formatted(name, name)).get("id").asText();
    }

    String service(Browser browser, String business, String price, String currency) throws Exception {
        return created(browser, "/api/businesses/" + business + "/services", """
                {"name":"Strategieberatung","description":"Workshop und Konzept","unit":"HOUR",
                 "unitPrice":"%s","currency":"%s","taxRate":"8.10"}
                """.formatted(price, currency)).get("id").asText();
    }

    Tenant tenant() throws Exception {
        Browser owner = login();
        String business = business(owner, "Studio Nord");
        return new Tenant(owner, business, customer(owner, business, "Muster"), service(owner, business, "95.10", "CHF"));
    }

    String base(String business) { return "/api/businesses/" + business + "/quotes"; }

    static String quote(String customer, String items) {
        return """
                {"customerId":"%s","issueDate":"2026-11-18","validUntil":"2026-12-18",
                 "introductionText":"Vielen Dank für Ihre Anfrage.","closingText":"Freundliche Grüsse",
                 "notes":"Intern: Rabatt möglich","items":[%s]}
                """.formatted(customer, items);
    }

    static String free(String description, String quantity, String price, String rate) {
        return """
                {"description":"%s","quantity":"%s","unit":"PIECE","unitPrice":"%s","taxRate":"%s"}
                """.formatted(description, quantity, price, rate);
    }

    JsonNode createQuote(Tenant tenant, String items) throws Exception {
        return created(tenant.owner(), base(tenant.business()), quote(tenant.customer(), items));
    }

    // ---------------------------------------------------------------- tests

    @Test void createAndEditDraftWithCatalogueAndFreeItemsUsingServerTotals() throws Exception {
        Tenant tenant = tenant();
        String body = quote(tenant.customer(), "{\"serviceId\":\"" + tenant.service() + "\",\"quantity\":\"1.5\"},"
                + free("Lizenz", "2", "100.00", "8.10"))
                .replace("\"items\"", "\"subtotal\":\"1.00\",\"taxTotal\":\"0\",\"grandTotal\":\"1.00\",\"status\":\"ACCEPTED\",\"quoteNumber\":\"X-1\",\"businessId\":\"" + UUID.randomUUID() + "\",\"items\"");
        var result = mvc.perform(write(post(base(tenant.business())), tenant.owner()).content(body)).andExpect(status().isCreated()).andReturn();
        JsonNode quote = JSON.readTree(result.getResponse().getContentAsString());
        String id = quote.get("id").asText();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(base(tenant.business()) + "/" + id);
        assertThat(quote.get("quoteNumber").asText()).isEqualTo("ANG-0001");
        assertThat(quote.get("status").asText()).isEqualTo("DRAFT");
        assertThat(quote.get("businessId").asText()).isEqualTo(tenant.business());
        assertThat(quote.get("currency").asText()).isEqualTo("CHF");
        JsonNode first = quote.get("items").get(0);
        assertThat(first.get("description").asText()).isEqualTo("Strategieberatung\nWorkshop und Konzept");
        assertThat(first.get("unit").asText()).isEqualTo("HOUR");
        assertThat(first.get("unitPrice").asText()).isEqualTo("95.10");
        assertThat(first.get("taxRate").asText()).isEqualTo("8.10");
        assertThat(first.get("quantity").asText()).isEqualTo("1.5");
        assertThat(first.get("lineSubtotal").asText()).isEqualTo("142.65");   // 1.5 × 95.10
        assertThat(first.get("lineTax").asText()).isEqualTo("11.55");         // 11.55465 → 11.55
        assertThat(first.get("lineTotal").asText()).isEqualTo("154.20");
        assertThat(first.get("serviceId").asText()).isEqualTo(tenant.service());
        assertThat(quote.get("items").get(1).get("serviceId").isNull()).isTrue();
        assertThat(quote.get("subtotal").asText()).isEqualTo("342.65");
        assertThat(quote.get("taxTotal").asText()).isEqualTo("27.75");       // 11.55 + 16.20
        assertThat(quote.get("grandTotal").asText()).isEqualTo("370.40");
        assertThat(quote.get("taxGroups").get(0).get("base").asText()).isEqualTo("342.65");
        assertThat(quote.get("customer").get("name").asText()).isEqualTo("Muster");
        assertThat(quote.get("customer").get("contactName").asText()).isEqualTo("Erika Muster");
        assertThat(quote.get("customer").get("customerNumber").asText()).isEqualTo("K-0001");

        // Edit: reorder, change quantity/price, add a third item; positions are renumbered.
        String path = base(tenant.business()) + "/" + id;
        JsonNode edited = ok(write(put(path), tenant.owner()).content(quote(tenant.customer(),
                free("Lizenz", "3", "100.00", "8.10") + ",{\"serviceId\":\"" + tenant.service() + "\",\"quantity\":\"2\",\"unitPrice\":\"90.00\"},"
                + free("Reise", "1", "45.50", "0")).replace("Freundliche Grüsse", "Beste Grüsse")));
        assertThat(edited.get("id").asText()).isEqualTo(id);
        assertThat(edited.get("quoteNumber").asText()).isEqualTo("ANG-0001");
        assertThat(edited.get("items")).hasSize(3);
        assertThat(edited.get("items").get(0).get("description").asText()).isEqualTo("Lizenz");
        assertThat(edited.get("items").get(1).get("unitPrice").asText()).isEqualTo("90.00");
        for (int index = 0; index < 3; index++) assertThat(edited.get("items").get(index).get("position").asInt()).isEqualTo(index + 1);
        assertThat(edited.get("subtotal").asText()).isEqualTo("525.50");    // 300 + 180 + 45.50
        assertThat(edited.get("taxTotal").asText()).isEqualTo("38.88");     // 24.30 + 14.58 + 0
        assertThat(edited.get("grandTotal").asText()).isEqualTo("564.38");
        assertThat(edited.get("closingText").asText()).isEqualTo("Beste Grüsse");
        assertThat(edited.get("taxGroups")).hasSize(2);
        assertThat(ok(get(path).session(tenant.owner().session())).get("grandTotal").asText()).isEqualTo("564.38");
        assertThat(jdbc.queryForObject("select count(*) from quote_items where quote_id = ?", Integer.class, UUID.fromString(id))).isEqualTo(3);
        assertThat(jdbc.queryForObject("select grand_total from quotes where id = ?", BigDecimal.class, UUID.fromString(id))).isEqualByComparingTo("564.38");

        // Remove all positions: allowed for drafts, but such a draft cannot be sent.
        JsonNode empty = ok(write(put(path), tenant.owner()).content(quote(tenant.customer(), "")));
        assertThat(empty.get("grandTotal").asText()).isEqualTo("0.00");
        mvc.perform(write(patch(path + "/send"), tenant.owner())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Quote has no items"));

        assertThat(createQuote(tenant, free("Zweites", "1", "1.00", "0")).get("quoteNumber").asText()).isEqualTo("ANG-0002");
        JsonNode list = ok(get(base(tenant.business())).session(tenant.owner().session()));
        assertThat(list).hasSize(2);
        assertThat(list.get(0).has("items")).isFalse();
        assertThat(list.get(0).has("notes")).isFalse();
    }

    @Test void calculatorRoundsHalfUpPerLineToTheCurrencyMinorUnit() {
        assertLine("0.333", "10.05", "7.70", "CHF", "3.35", "0.26");         // 3.34665 / 0.25795
        assertLine("0.5", "0.01", "0", "CHF", "0.01", "0.00");               // 0.005 rounds up
        assertLine("1", "0.05", "8.10", "CHF", "0.05", "0.00");              // 0.00405 rounds down
        assertLine("1", "0.07", "8.10", "CHF", "0.07", "0.01");              // 0.00567 rounds up
        assertLine("3", "33.33", "100", "EUR", "99.99", "99.99");
        assertLine("1.5", "333", "10", "JPY", "500.00", "50.00");            // 499.5 → 500 (no minor unit)
        var lines = List.of(QuoteCalculator.line(new BigDecimal("0.333"), new BigDecimal("1.00"), BigDecimal.ZERO, "CHF"),
                QuoteCalculator.line(new BigDecimal("0.333"), new BigDecimal("1.00"), BigDecimal.ZERO, "CHF"),
                QuoteCalculator.line(new BigDecimal("0.333"), new BigDecimal("1.00"), BigDecimal.ZERO, "CHF"));
        assertThat(QuoteCalculator.totals(lines).subtotal()).isEqualByComparingTo("0.99"); // sum of rounded lines
        assertThat(QuoteCalculator.supported("CHF")).isTrue();
        assertThat(QuoteCalculator.supported("JPY")).isTrue();
        assertThat(QuoteCalculator.supported("KWD")).isFalse();             // three minor digits
        assertThat(QuoteCalculator.supported("ZZZ")).isFalse();
    }

    void assertLine(String quantity, String price, String rate, String currency, String subtotal, String tax) {
        var line = QuoteCalculator.line(new BigDecimal(quantity), new BigDecimal(price), new BigDecimal(rate), currency);
        assertThat(line.subtotal().toPlainString()).isEqualTo(subtotal);
        assertThat(line.tax().toPlainString()).isEqualTo(tax);
        assertThat(line.total()).isEqualByComparingTo(line.subtotal().add(line.tax()));
        assertThat(line.subtotal().scale()).isEqualTo(2);
    }

    @Test void validationRejectsInvalidInputWithoutCreatingQuotesOrNumbers() throws Exception {
        Tenant tenant = tenant();
        String valid = free("Arbeit", "1", "10.00", "8.10");
        List<String[]> cases = List.of(
                new String[] {"{}", "customerId"},
                new String[] {quote(tenant.customer(), valid).replace("2026-12-18", "2026-11-17"), "validUntil"},
                new String[] {quote(tenant.customer(), free("Arbeit", "0", "10.00", "8.10")), "items[0].quantity"},
                new String[] {quote(tenant.customer(), free("Arbeit", "-1", "10.00", "8.10")), "items[0].quantity"},
                new String[] {quote(tenant.customer(), free("Arbeit", "1.0001", "10.00", "8.10")), "items[0].quantity"},
                new String[] {quote(tenant.customer(), free("Arbeit", "1", "10.001", "8.10")), "items[0].unitPrice"},
                new String[] {quote(tenant.customer(), free("Arbeit", "1", "-0.01", "8.10")), "items[0].unitPrice"},
                new String[] {quote(tenant.customer(), free("Arbeit", "1", "10.00", "100.01")), "items[0].taxRate"},
                new String[] {quote(tenant.customer(), free(" ", "1", "10.00", "8.10")), "items[0].description"},
                new String[] {quote(tenant.customer(), "{\"quantity\":\"1\"}"), "items[0].description"},
                new String[] {quote(tenant.customer(), valid).replace("\"items\"", "\"currency\":\"ZZZ\",\"items\""), "currency"},
                new String[] {quote(tenant.customer(), valid).replace("\"items\"", "\"currency\":\"KWD\",\"items\""), "currency"},
                new String[] {quote(tenant.customer(), free("Arbeit", "1", "10.50", "0")).replace("\"items\"", "\"currency\":\"JPY\",\"items\""), "items[0].unitPrice"},
                new String[] {quote(tenant.customer(), free("Arbeit", "999999999.999", "99999999999999999.99", "0")), "items[0].unitPrice"},
                new String[] {quote(tenant.customer(), String.join(",", Collections.nCopies(201, valid))), "items"},
                new String[] {quote(tenant.customer(), valid).replace("Freundliche Grüsse", "x".repeat(4001)), "closingText"});
        for (String[] invalid : cases) {
            mvc.perform(write(post(base(tenant.business())), tenant.owner()).content(invalid[0]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Invalid request"))
                    .andExpect(jsonPath("$.fields['" + invalid[1] + "']").exists());
        }
        for (String malformed : List.of("not json", quote(tenant.customer(), valid).replace("2026-11-18", "18.11.2026"),
                quote(tenant.customer(), free("Arbeit", "NaN", "1", "0")), quote(tenant.customer(), valid).replace("PIECE", "YEAR"))) {
            mvc.perform(write(post(base(tenant.business())), tenant.owner()).content(malformed)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from quotes where business_id = ?", Integer.class, UUID.fromString(tenant.business()))).isZero();
        assertThat(createQuote(tenant, valid).get("quoteNumber").asText()).isEqualTo("ANG-0001"); // no number consumed
        mvc.perform(get(base(tenant.business())).param("status", "OPEN").session(tenant.owner().session())).andExpect(status().isBadRequest());
        mvc.perform(get(base(tenant.business())).param("search", "x".repeat(321)).session(tenant.owner().session())).andExpect(status().isBadRequest());
    }

    @Test void statusTransitionsFollowTheDocumentedRules() throws Exception {
        Tenant tenant = tenant();
        String id = createQuote(tenant, free("Arbeit", "1", "100.00", "8.10")).get("id").asText();
        String path = base(tenant.business()) + "/" + id;
        for (String action : List.of("accept", "reject")) {
            mvc.perform(write(patch(path + "/" + action), tenant.owner())).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("Invalid status transition"));
        }
        JsonNode sent = ok(write(patch(path + "/send"), tenant.owner()));
        assertThat(sent.get("status").asText()).isEqualTo("SENT");
        assertThat(sent.get("sentAt").isNull()).isFalse();
        mvc.perform(write(patch(path + "/send"), tenant.owner())).andExpect(status().isConflict());
        mvc.perform(write(put(path), tenant.owner()).content(quote(tenant.customer(), free("Neu", "1", "1.00", "0"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Quote is not editable"));
        mvc.perform(write(delete(path), tenant.owner())).andExpect(status().isConflict());
        JsonNode accepted = ok(write(patch(path + "/accept"), tenant.owner()));
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(accepted.get("decidedAt").isNull()).isFalse();
        assertThat(accepted.get("grandTotal").asText()).isEqualTo("108.10");
        for (String action : List.of("send", "accept", "reject")) {
            mvc.perform(write(patch(path + "/" + action), tenant.owner())).andExpect(status().isConflict());
        }
        mvc.perform(write(put(path), tenant.owner()).content(quote(tenant.customer(), free("Neu", "1", "1.00", "0")))).andExpect(status().isConflict());
        mvc.perform(write(delete(path), tenant.owner())).andExpect(status().isConflict());
        assertThat(ok(get(path).session(tenant.owner().session())).get("items").get(0).get("description").asText()).isEqualTo("Arbeit");

        // Database guard: an accepted quote cannot be altered, deleted or extended even by direct SQL.
        UUID quoteId = UUID.fromString(id);
        assertThatThrownBy(() -> jdbc.update("update quotes set grand_total = 1, subtotal = 1, tax_total = 0 where id = ?", quoteId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update quotes set status = 'DRAFT', sent_at = null, decided_at = null where id = ?", quoteId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update quote_items set description = 'x' where quote_id = ?", quoteId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from quote_items where quote_id = ?", quoteId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from quotes where id = ?", quoteId))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Rejection path and deletion of drafts.
        String second = base(tenant.business()) + "/" + createQuote(tenant, free("Arbeit", "1", "1.00", "0")).get("id").asText();
        ok(write(patch(second + "/send"), tenant.owner()));
        assertThat(ok(write(patch(second + "/reject"), tenant.owner())).get("status").asText()).isEqualTo("REJECTED");
        mvc.perform(write(patch(second + "/accept"), tenant.owner())).andExpect(status().isConflict());
        String draft = base(tenant.business()) + "/" + createQuote(tenant, free("Arbeit", "1", "1.00", "0")).get("id").asText();
        mvc.perform(write(delete(draft), tenant.owner())).andExpect(status().isNoContent());
        mvc.perform(get(draft).session(tenant.owner().session())).andExpect(status().isNotFound());
        assertThat(ok(get(base(tenant.business())).param("status", "REJECTED").session(tenant.owner().session()))).hasSize(1);
        assertThat(ok(get(base(tenant.business())).param("search", "muster").session(tenant.owner().session()))).hasSize(2);
        assertThat(ok(get(base(tenant.business())).param("search", "ANG-0001").session(tenant.owner().session()))).hasSize(1);
    }

    @Test void snapshotsProtectQuotesFromCatalogueAndCustomerChanges() throws Exception {
        Tenant tenant = tenant();
        String item = "{\"serviceId\":\"" + tenant.service() + "\",\"quantity\":\"1\"}";
        String id = createQuote(tenant, item).get("id").asText();
        String path = base(tenant.business()) + "/" + id;
        String servicePath = "/api/businesses/" + tenant.business() + "/services/" + tenant.service();
        mvc.perform(write(put(servicePath), tenant.owner()).content("""
                {"name":"Neuer Name","unit":"DAY","unitPrice":"500.00","taxRate":"2.60"}""")).andExpect(status().isOk());
        mvc.perform(write(patch(servicePath + "/archive"), tenant.owner())).andExpect(status().isOk());
        JsonNode unchanged = ok(get(path).session(tenant.owner().session()));
        assertThat(unchanged.get("items").get(0).get("unitPrice").asText()).isEqualTo("95.10");
        assertThat(unchanged.get("items").get(0).get("description").asText()).startsWith("Strategieberatung");
        assertThat(unchanged.get("grandTotal").asText()).isEqualTo("102.80");

        // An archived service stays in drafts that already use it (with explicit snapshot values)...
        String retained = "{\"serviceId\":\"" + tenant.service() + "\",\"description\":\"Strategieberatung\",\"quantity\":\"2\",\"unit\":\"HOUR\",\"unitPrice\":\"95.10\",\"taxRate\":\"8.10\"}";
        JsonNode kept = ok(write(put(path), tenant.owner()).content(quote(tenant.customer(), retained)));
        assertThat(kept.get("items").get(0).get("serviceId").asText()).isEqualTo(tenant.service());
        assertThat(kept.get("subtotal").asText()).isEqualTo("190.20");
        // ...but cannot be added to other quotes.
        mvc.perform(write(post(base(tenant.business())), tenant.owner()).content(quote(tenant.customer(), retained)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields['items[0].serviceId']").exists());

        // Customer data is refreshed while drafting and frozen when the quote is sent.
        String customerPath = "/api/businesses/" + tenant.business() + "/customers/" + tenant.customer();
        String customer = """
                {"type":"COMPANY","displayName":"%s","companyName":"Muster AG","street":"%s","postalCode":"3000","city":"Bern","country":"CH"}""";
        mvc.perform(write(put(customerPath), tenant.owner()).content(customer.formatted("Muster Neu", "Marktgasse 2"))).andExpect(status().isOk());
        JsonNode sent = ok(write(patch(path + "/send"), tenant.owner()));
        assertThat(sent.get("customer").get("name").asText()).isEqualTo("Muster Neu");
        assertThat(sent.get("customer").get("street").asText()).isEqualTo("Marktgasse 2");
        mvc.perform(write(put(customerPath), tenant.owner()).content(customer.formatted("Später", "Andere 9"))).andExpect(status().isOk());
        mvc.perform(write(patch(customerPath + "/archive"), tenant.owner())).andExpect(status().isOk());
        JsonNode frozen = ok(get(path).session(tenant.owner().session()));
        assertThat(frozen.get("customer").get("name").asText()).isEqualTo("Muster Neu");
        assertThat(frozen.get("customer").get("city").asText()).isEqualTo("Bern");
        assertThat(pdfText(tenant.owner(), path)).contains("Muster Neu").contains("Marktgasse 2").doesNotContain("Später");
        // Archived customers cannot be used for new quotes.
        mvc.perform(write(post(base(tenant.business())), tenant.owner()).content(quote(tenant.customer(), free("A", "1", "1", "0"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.customerId").exists());
    }

    @Test void foreignAndUnknownResourcesAreIndistinguishable() throws Exception {
        Tenant alice = tenant();
        String aliceSecond = business(alice.owner(), "Zweitfirma");
        Tenant bob = tenant();
        String id = createQuote(alice, free("Arbeit", "1", "1.00", "0")).get("id").asText();
        String expected = mvc.perform(get(base(alice.business()) + "/" + UUID.randomUUID()).session(alice.owner().session()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(expected).contains("Quote not found");
        String body = quote(alice.customer(), free("Arbeit", "1", "1.00", "0"));
        for (String scope : List.of(aliceSecond, bob.business(), UUID.randomUUID().toString())) {
            String path = base(scope) + "/" + id;
            mvc.perform(get(path).session(alice.owner().session())).andExpect(status().isNotFound()).andExpect(content().string(expected));
            mvc.perform(get(path + "/pdf").session(alice.owner().session())).andExpect(status().isNotFound()).andExpect(content().string(expected));
            mvc.perform(write(put(path), alice.owner()).content(body)).andExpect(status().isNotFound()).andExpect(content().string(expected));
            mvc.perform(write(delete(path), alice.owner())).andExpect(status().isNotFound()).andExpect(content().string(expected));
            for (String action : List.of("send", "accept", "reject")) {
                mvc.perform(write(patch(path + "/" + action), alice.owner())).andExpect(status().isNotFound()).andExpect(content().string(expected));
            }
        }
        // Bob cannot reach Alice's business at all, even with Alice's IDs.
        mvc.perform(get(base(alice.business())).session(bob.owner().session())).andExpect(status().isNotFound()).andExpect(content().string(expected));
        mvc.perform(get(base(alice.business()) + "/" + id + "/pdf").session(bob.owner().session())).andExpect(status().isNotFound()).andExpect(content().string(expected));
        mvc.perform(write(post(base(alice.business())), bob.owner()).content(body)).andExpect(status().isNotFound());
        // Foreign customer/service IDs are rejected exactly like unknown IDs.
        String unknownCustomer = mvc.perform(write(post(base(bob.business())), bob.owner()).content(quote(UUID.randomUUID().toString(), free("A", "1", "1", "0"))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String foreignCustomer = mvc.perform(write(post(base(bob.business())), bob.owner()).content(quote(alice.customer(), free("A", "1", "1", "0"))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(foreignCustomer).isEqualTo(unknownCustomer).contains("customerId");
        String unknownService = mvc.perform(write(post(base(bob.business())), bob.owner()).content(quote(bob.customer(), "{\"serviceId\":\"" + UUID.randomUUID() + "\",\"quantity\":\"1\"}")))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        String foreignService = mvc.perform(write(post(base(bob.business())), bob.owner()).content(quote(bob.customer(), "{\"serviceId\":\"" + alice.service() + "\",\"quantity\":\"1\"}")))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(foreignService).isEqualTo(unknownService).contains("items[0].serviceId");
        // Editing a draft cannot swap in a foreign customer either.
        mvc.perform(write(put(base(alice.business()) + "/" + id), alice.owner()).content(quote(bob.customer(), free("A", "1", "1", "0"))))
                .andExpect(status().isBadRequest());
        assertThat(ok(get(base(bob.business())).session(bob.owner().session()))).isEmpty();
        // The database refuses cross-tenant references even if the service layer were bypassed.
        assertThatThrownBy(() -> jdbc.update("update quotes set customer_id = ? where id = ?", UUID.fromString(bob.customer()), UUID.fromString(id)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A MEMBER of Alice's business has the same quote rights as the OWNER.
        memberships.saveAndFlush(new Membership(bob.owner().user(), businesses.findById(UUID.fromString(alice.business())).orElseThrow(), MembershipRole.MEMBER));
        assertThat(ok(get(base(alice.business()) + "/" + id).session(bob.owner().session())).get("id").asText()).isEqualTo(id);
        created(bob.owner(), base(alice.business()), body);
    }

    @Test void sessionAndCsrfAreRequired() throws Exception {
        Tenant tenant = tenant();
        String path = base(tenant.business()) + "/" + createQuote(tenant, free("Arbeit", "1", "1.00", "0")).get("id").asText();
        String body = quote(tenant.customer(), free("Arbeit", "1", "1.00", "0"));
        for (String read : List.of(base(tenant.business()), path, path + "/pdf")) mvc.perform(get(read)).andExpect(status().isUnauthorized());
        Browser anonymous = anonymous();
        mvc.perform(write(post(base(tenant.business())), anonymous).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(base(tenant.business())).session(tenant.owner().session()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put(path).session(tenant.owner().session()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(delete(path).session(tenant.owner().session())).andExpect(status().isForbidden());
        for (String action : List.of("send", "accept", "reject")) {
            mvc.perform(patch(path + "/" + action).session(tenant.owner().session()).header("X-CSRF-TOKEN", "forged")).andExpect(status().isForbidden());
        }
        assertThat(ok(get(path).session(tenant.owner().session())).get("status").asText()).isEqualTo("DRAFT");
    }

    @Test void concurrentCreationAssignsUniqueSequentialNumbersPerBusiness() throws Exception {
        Tenant tenant = tenant();
        Tenant other = tenant();
        int count = 16;
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        QuoteRequest request = JSON.readValue(quote(tenant.customer(), free("Arbeit", "1", "1.00", "0")), QuoteRequest.class);
        try {
            for (int index = 0; index < count; index++) {
                results.add(pool.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(tenant.owner().user().getEmail(), null, List.of()));
                    try {
                        start.await();
                        return quoteService.create(UUID.fromString(tenant.business()), request).quoteNumber();
                    } finally { SecurityContextHolder.clearContext(); }
                }));
            }
            start.countDown();
            Set<String> numbers = new TreeSet<>();
            for (Future<String> result : results) numbers.add(result.get(60, TimeUnit.SECONDS));
            Set<String> expected = new TreeSet<>();
            for (int index = 1; index <= count; index++) expected.add(String.format("ANG-%04d", index));
            assertThat(numbers).isEqualTo(expected);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("select last_value from quote_number_counters where business_id = ?", Long.class,
                UUID.fromString(tenant.business()))).isEqualTo((long) count);
        assertThat(createQuote(other, free("Arbeit", "1", "1.00", "0")).get("quoteNumber").asText()).isEqualTo("ANG-0001");
        assertThatThrownBy(() -> jdbc.update("update quotes set quote_number = 'ANG-0002' where business_id = ? and quote_number = 'ANG-0001'",
                UUID.fromString(tenant.business()))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void pdfIsGeneratedAndDownloadedWithDocumentContent() throws Exception {
        Tenant tenant = tenant();
        String items = "{\"serviceId\":\"" + tenant.service() + "\",\"quantity\":\"10\"},"
                + free("Lizenz mit Emoji 🚀 und 漢字", "2.5", "1234.50", "0");
        String path = base(tenant.business()) + "/" + createQuote(tenant, items).get("id").asText();
        var response = mvc.perform(get(path + "/pdf").session(tenant.owner().session())).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Angebot-ANG-0001.pdf")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse();
        byte[] bytes = response.getContentAsByteArray();
        assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
        String text = extract(bytes);
        assertThat(text).contains("Studio Nord", "Muster", "Muster AG", "Erika Muster", "Bahnhofstrasse 1", "8001 Zürich",
                "Angebot ANG-0001", "18.11.2026", "18.12.2026", "K-0001", "Strategieberatung", "Workshop und Konzept",
                "10 Std.", "95.10", "951.00", "2.5 Stk.", "1’234.50", "3’086.25", "8.1 %", "MWST 8.1 % auf 951.00",
                "CHF 77.03", "Total", "CHF 4’114.28", "Vielen Dank für Ihre Anfrage.", "Freundliche Grüsse", "Seite 1 von 1");
        assertThat(text.replaceAll("\\s+", "")).contains("ENTWURF");  // letter-spaced label
        assertThat(text).doesNotContain("Intern: Rabatt möglich");  // internal notes stay internal
        assertThat(text).contains("Lizenz mit Emoji ? und ??");

        // Long quotes paginate; sent quotes drop the draft marker.
        String many = String.join(",", Collections.nCopies(70, free("Position mit einer etwas längeren Beschreibung, die umbrechen darf", "1", "10.00", "8.10")));
        String longPath = base(tenant.business()) + "/" + createQuote(tenant, many).get("id").asText();
        ok(write(patch(longPath + "/send"), tenant.owner()));
        String longText = pdfText(tenant.owner(), longPath);
        assertThat(longText).contains("Seite 1 von", "Seite 2 von", "Angebot ANG-0002", "CHF 756.70");
        assertThat(longText.replaceAll("\\s+", "")).doesNotContain("ENTWURF");
        try (var document = Loader.loadPDF(mvc.perform(get(longPath + "/pdf").session(tenant.owner().session())).andReturn().getResponse().getContentAsByteArray())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Angebot ANG-0002");
        }
    }

    String pdfText(Browser browser, String path) throws Exception {
        return extract(mvc.perform(get(path + "/pdf").session(browser.session())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    static String extract(byte[] bytes) throws Exception {
        try (var document = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(document); }
    }

    @Test void upgradeFromV5PreservesDataAndAddsConstraints() {
        String schema = "quote_upgrade_test";
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("5").load().migrate();
            UUID business = UUID.randomUUID(); UUID other = UUID.randomUUID(); UUID customer = UUID.randomUUID(); UUID foreign = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".businesses (id,name) values (?, 'Existing'), (?, 'Other')", business, other);
            jdbc.update("insert into " + schema + ".customers (id,business_id,type,display_name,customer_number) values (?,?,'COMPANY','Kunde','K-0001'), (?,?,'COMPANY','Fremd','K-0001')",
                    customer, business, foreign, other);
            var flyway = org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("6").load();
            flyway.migrate(); flyway.validate();
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("6");
            assertThat(jdbc.queryForObject("select display_name from " + schema + ".customers where id = ?", String.class, customer)).isEqualTo("Kunde");
            String insert = "insert into " + schema + ".quotes (id,business_id,customer_id,quote_number,issue_date,valid_until,currency,subtotal,tax_total,grand_total,sender_name,customer_number,customer_name,customer_country)"
                    + " values (?,?,?,?,?,?,'CHF',?,?,?,'Existing','K-0001','Kunde','CH')";
            LocalDate day = LocalDate.of(2026, 11, 18);
            UUID quote = UUID.randomUUID();
            jdbc.update(insert, quote, business, customer, "ANG-0001", day, day, new BigDecimal("10.00"), new BigDecimal("0.81"), new BigDecimal("10.81"));
            assertThat(jdbc.queryForObject("select status from " + schema + ".quotes where id = ?", String.class, quote)).isEqualTo("DRAFT");
            for (Object[] invalid : List.of(
                    new Object[] {UUID.randomUUID(), business, foreign, "ANG-0002", day, day, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE},     // foreign customer
                    new Object[] {UUID.randomUUID(), business, customer, "ANG-0001", day, day, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE},    // duplicate number
                    new Object[] {UUID.randomUUID(), business, customer, "ANG-0003", day, day.minusDays(1), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE}, // validity
                    new Object[] {UUID.randomUUID(), business, customer, "ANG-0004", day, day, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE},     // totals
                    new Object[] {UUID.randomUUID(), business, customer, "Q-1", day, day, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE})) {      // format
                assertThatThrownBy(() -> jdbc.update(insert, invalid)).isInstanceOf(DataIntegrityViolationException.class);
            }
            jdbc.update("update " + schema + ".quotes set status = 'SENT', sent_at = now() where id = ?", quote);
            assertThatThrownBy(() -> jdbc.update("update " + schema + ".quotes set notes = 'x' where id = ?", quote))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }
}
