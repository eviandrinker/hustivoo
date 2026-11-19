package digital.margraf.hustivoo_backend.invoice;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class InvoiceTests extends PostgresIntegrationTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PASSWORD = "ExamplePassword123!";
    static final String SETTINGS = """
            {"name":"Studio Nord GmbH","street":"Seestrasse 12","postalCode":"8002","city":"Zürich","country":"CH",
             "iban":"CH93 0076 2011 6238 5295 7","bankName":"Zürcher Kantonalbank","vatNumber":"CHE-116.281.710 MWST"}""";
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired InvoiceService invoiceService;
    @Autowired javax.sql.DataSource dataSource;

    record Browser(User user, MockHttpSession session, String csrf) {}
    record Tenant(Browser owner, String business, String customer, String service) {}

    // ---------------------------------------------------------------- fixtures

    Browser login() throws Exception {
        User user = users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", encoder.encode(PASSWORD)));
        var anonymous = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var result = mvc.perform(post("/api/v1/auth/login").session((MockHttpSession) anonymous.getRequest().getSession())
                .header("X-CSRF-TOKEN", JSON.readTree(anonymous.getResponse().getContentAsString()).get("token").asText())
                .contentType("application/json")
                .content(JSON.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();
        var csrf = mvc.perform(get("/api/v1/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return new Browser(user, session, JSON.readTree(csrf.getResponse().getContentAsString()).get("token").asText());
    }

    MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request, Browser browser) {
        return request.session(browser.session()).header("X-CSRF-TOKEN", browser.csrf()).contentType("application/json");
    }

    JsonNode json(MockHttpServletRequestBuilder request, int status) throws Exception {
        return JSON.readTree(mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
    }

    Tenant tenant() throws Exception {
        Browser owner = login();
        String business = json(write(post("/api/businesses"), owner).content("{\"name\":\"Studio Nord\",\"currency\":\"CHF\"}"), 201).get("id").asText();
        json(write(put("/api/businesses/" + business + "/settings"), owner).content(SETTINGS), 200);
        String customer = json(write(post("/api/businesses/" + business + "/customers"), owner).content("""
                {"type":"COMPANY","displayName":"Muster AG","companyName":"Muster AG","street":"Bahnhofstrasse 1",
                 "postalCode":"3000","city":"Bern","country":"CH"}"""), 201).get("id").asText();
        String service = json(write(post("/api/businesses/" + business + "/services"), owner).content("""
                {"name":"Strategieberatung","unit":"HOUR","unitPrice":"95.10","currency":"CHF","taxRate":"8.10"}"""), 201).get("id").asText();
        return new Tenant(owner, business, customer, service);
    }

    static String base(String business) { return "/api/businesses/" + business + "/invoices"; }

    static String invoice(String customer, String items, String issueDate, int term) {
        return """
                {"customerId":"%s","issueDate":"%s","paymentTermDays":%d,
                 "introductionText":"Vielen Dank für Ihren Auftrag.","closingText":"Freundliche Grüsse",
                 "notes":"Intern: Mahnung erst nach 45 Tagen","items":[%s]}
                """.formatted(customer, issueDate, term, items);
    }

    static String free(String description, String quantity, String price, String rate) {
        return """
                {"description":"%s","quantity":"%s","unit":"PIECE","unitPrice":"%s","taxRate":"%s"}""".formatted(description, quantity, price, rate);
    }

    JsonNode create(Tenant tenant, String items) throws Exception {
        return json(write(post(base(tenant.business())), tenant.owner()).content(invoice(tenant.customer(), items, "2026-11-18", 30)), 201);
    }

    String path(Tenant tenant, JsonNode invoice) { return base(tenant.business()) + "/" + invoice.get("id").asText(); }

    JsonNode pay(Tenant tenant, String path, String amount, int status) throws Exception {
        return json(write(post(path + "/payments"), tenant.owner()).content("""
                {"paymentDate":"2026-11-18","amount":"%s","method":"BANK_TRANSFER","reference":"Bankauszug 42"}""".formatted(amount)), status);
    }

    static String pdfText(byte[] bytes) throws Exception {
        try (var document = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(document); }
    }

    // ---------------------------------------------------------------- tests

    @Test void createsDraftsWithServerCalculatedAmountsAndDueDate() throws Exception {
        Tenant tenant = tenant();
        String body = invoice(tenant.customer(), "{\"serviceId\":\"" + tenant.service() + "\",\"quantity\":\"1.5\"}," + free("Lizenz", "2", "100.00", "0"),
                "2026-11-18", 30).replace("\"items\"", "\"grandTotal\":\"1.00\",\"status\":\"PAID\",\"paidTotal\":\"999\",\"dueDate\":\"2030-01-01\",\"invoiceNumber\":\"X\",\"items\"");
        var result = mvc.perform(write(post(base(tenant.business())), tenant.owner()).content(body)).andExpect(status().isCreated()).andReturn();
        JsonNode invoice = JSON.readTree(result.getResponse().getContentAsString());
        assertThat(result.getResponse().getHeader("Location")).isEqualTo(base(tenant.business()) + "/" + invoice.get("id").asText());
        assertThat(invoice.get("invoiceNumber").asText()).isEqualTo("RE-0001");
        assertThat(invoice.get("status").asText()).isEqualTo("DRAFT");
        assertThat(invoice.get("dueDate").asText()).isEqualTo("2026-12-18");
        // 1.5 × 95.10 = 142.65, MWST 8.1 % = 11.55; 2 × 100.00 = 200.00 without tax — identical to quotes.
        assertThat(invoice.get("subtotal").asText()).isEqualTo("342.65");
        assertThat(invoice.get("taxTotal").asText()).isEqualTo("11.55");
        assertThat(invoice.get("grandTotal").asText()).isEqualTo("354.20");
        assertThat(invoice.get("paidTotal").asText()).isEqualTo("0.00");
        assertThat(invoice.get("openAmount").asText()).isEqualTo("0.00");   // drafts are not receivables yet
        assertThat(invoice.get("taxGroups")).hasSize(2);
        assertThat(invoice.get("items").get(0).get("description").asText()).isEqualTo("Strategieberatung");
        assertThat(invoice.get("qrBill").get("available").asBoolean()).isTrue();

        // Validation: field errors, nothing stored and no number consumed.
        Integer before = jdbc.queryForObject("select count(*) from invoices", Integer.class);
        JsonNode error = json(write(post(base(tenant.business())), tenant.owner())
                .content(invoice(tenant.customer(), free("A", "-1", "1", "0"), "2026-11-18", 400)), 400);
        assertThat(error.get("fields").has("paymentTermDays")).isTrue();
        assertThat(error.get("fields").has("items[0].quantity")).isTrue();
        json(write(post(base(tenant.business())), tenant.owner()).content(invoice(UUID.randomUUID().toString(), free("A", "1", "1", "0"), "2026-11-18", 30)), 400);
        assertThat(jdbc.queryForObject("select count(*) from invoices", Integer.class)).isEqualTo(before);
        assertThat(json(write(post(base(tenant.business())), tenant.owner()).content(invoice(tenant.customer(), "", "2026-11-18", 0)), 201)
                .get("invoiceNumber").asText()).isEqualTo("RE-0002");

        // Drafts can be edited and deleted.
        String path = path(tenant, invoice);
        JsonNode edited = json(write(put(path), tenant.owner()).content(invoice(tenant.customer(), free("Neu", "3", "10.00", "8.10"), "2026-12-01", 10)), 200);
        assertThat(edited.get("grandTotal").asText()).isEqualTo("32.43");
        assertThat(edited.get("dueDate").asText()).isEqualTo("2026-12-11");
        mvc.perform(write(delete(path), tenant.owner())).andExpect(status().isNoContent());
        json(get(path).session(tenant.owner().session()), 404);
    }

    @Test void issuingFreezesTheInvoiceAndOnlyPaymentsChangeItsStatus() throws Exception {
        Tenant tenant = tenant();
        JsonNode empty = json(write(post(base(tenant.business())), tenant.owner()).content(invoice(tenant.customer(), "", "2026-11-18", 30)), 201);
        assertThat(json(write(patch(path(tenant, empty) + "/issue"), tenant.owner()), 409).get("error").asText()).isEqualTo("Invoice has no items");

        String path = path(tenant, create(tenant, free("Konzept", "1", "1000.00", "8.10")));
        JsonNode issued = json(write(patch(path + "/issue"), tenant.owner()), 200);
        assertThat(issued.get("status").asText()).isEqualTo("ISSUED");
        assertThat(issued.get("openAmount").asText()).isEqualTo("1081.00");
        assertThat(issued.get("issuedAt").isNull()).isFalse();
        json(write(patch(path + "/issue"), tenant.owner()), 409);
        assertThat(json(write(put(path), tenant.owner()).content(invoice(tenant.customer(), free("X", "1", "1", "0"), "2026-11-18", 30)), 409)
                .get("error").asText()).isEqualTo("Invoice is not editable");
        json(write(delete(path), tenant.owner()), 409);
        // The database refuses direct changes to issued content, items and deletion as well.
        UUID id = UUID.fromString(issued.get("id").asText());
        assertThatThrownBy(() -> jdbc.update("update invoices set grand_total = 1, subtotal = 1, tax_total = 0 where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update invoice_items set description = 'x' where invoice_id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from invoices where id = ?", id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update invoices set status = 'PAID' where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);   // status must match the payments
    }

    @Test void paymentsSupportPartialFullReversalAndPreventOverpayment() throws Exception {
        Tenant tenant = tenant();
        String path = path(tenant, create(tenant, free("Konzept", "1", "1000.00", "8.10")));
        json(write(post(path + "/payments"), tenant.owner()).content("""
                {"paymentDate":"2026-11-18","amount":"10.00","method":"CASH"}"""), 409);   // drafts accept no payments
        json(write(patch(path + "/issue"), tenant.owner()), 200);

        JsonNode partial = pay(tenant, path, "500.00", 201);
        assertThat(partial.get("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(partial.get("paidTotal").asText()).isEqualTo("500.00");
        assertThat(partial.get("openAmount").asText()).isEqualTo("581.00");
        assertThat(partial.get("payments").get(0).get("reference").asText()).isEqualTo("Bankauszug 42");

        assertThat(pay(tenant, path, "581.01", 400).get("fields").has("amount")).isTrue();      // overpayment
        assertThat(pay(tenant, path, "-1", 400).get("fields").has("amount")).isTrue();          // negative
        assertThat(pay(tenant, path, "0.001", 400).get("fields").has("amount")).isTrue();       // sub-cent
        json(write(post(path + "/payments"), tenant.owner()).content("""
                {"paymentDate":"2999-01-01","amount":"1.00","method":"CASH"}"""), 400);          // future date
        json(write(post(path + "/payments"), tenant.owner()).content("""
                {"paymentDate":"2026-11-18","amount":"1.00","method":"BITCOIN"}"""), 400);       // unknown method

        JsonNode paid = pay(tenant, path, "581.00", 201);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("openAmount").asText()).isEqualTo("0.00");
        pay(tenant, path, "0.01", 409);                                                          // nothing open any more
        json(write(patch(path + "/cancel"), tenant.owner()).content("{\"reason\":\"Fehler\"}"), 409);

        // Corrections: the payment is reversed with a reason, never edited or deleted.
        String payment = paid.get("payments").get(1).get("id").asText();
        json(write(patch(path + "/payments/" + payment + "/reverse"), tenant.owner()).content("{\"reason\":\" \"}"), 400);
        JsonNode reversed = json(write(patch(path + "/payments/" + payment + "/reverse"), tenant.owner()).content("{\"reason\":\"Betrag falsch erfasst\"}"), 200);
        assertThat(reversed.get("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(reversed.get("paidTotal").asText()).isEqualTo("500.00");
        assertThat(reversed.get("payments")).hasSize(2);
        assertThat(reversed.get("payments").get(1).get("reversed").asBoolean()).isTrue();
        assertThat(reversed.get("payments").get(1).get("reversalReason").asText()).isEqualTo("Betrag falsch erfasst");
        json(write(patch(path + "/payments/" + payment + "/reverse"), tenant.owner()).content("{\"reason\":\"nochmals\"}"), 409);
        UUID paymentId = UUID.fromString(payment);
        assertThatThrownBy(() -> jdbc.update("update invoice_payments set amount = 1 where id = ?", paymentId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from invoice_payments where id = ?", paymentId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(json(get(path + "/payments").session(tenant.owner().session()), 200)).hasSize(2);
    }

    @Test void cancellationRequiresAReasonAndNoActivePayments() throws Exception {
        Tenant tenant = tenant();
        String path = path(tenant, create(tenant, free("Konzept", "1", "100.00", "0")));
        json(write(patch(path + "/cancel"), tenant.owner()).content("{\"reason\":\"x\"}"), 409);   // drafts are deleted instead
        json(write(patch(path + "/issue"), tenant.owner()), 200);
        JsonNode payment = pay(tenant, path, "40.00", 201);
        assertThat(json(write(patch(path + "/cancel"), tenant.owner()).content("{\"reason\":\"Doppelt\"}"), 409).get("error").asText())
                .isEqualTo("Reverse the payments before cancelling");
        json(write(patch(path + "/payments/" + payment.get("payments").get(0).get("id").asText() + "/reverse"), tenant.owner())
                .content("{\"reason\":\"Rückerstattet\"}"), 200);
        json(write(patch(path + "/cancel"), tenant.owner()).content("{}"), 400);
        JsonNode cancelled = json(write(patch(path + "/cancel"), tenant.owner()).content("{\"reason\":\"Doppelt erfasst\"}"), 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("cancellationReason").asText()).isEqualTo("Doppelt erfasst");
        assertThat(cancelled.get("openAmount").asText()).isEqualTo("0.00");
        assertThat(cancelled.get("qrBill").get("available").asBoolean()).isFalse();
        pay(tenant, path, "1.00", 409);
        json(write(patch(path + "/cancel"), tenant.owner()).content("{\"reason\":\"nochmals\"}"), 409);
    }

    @Test void overdueIsDerivedAndFilterable() throws Exception {
        Tenant tenant = tenant();
        String overdue = path(tenant, json(write(post(base(tenant.business())), tenant.owner())
                .content(invoice(tenant.customer(), free("Alt", "1", "100.00", "0"), "2026-01-05", 10)), 201));
        json(write(patch(overdue + "/issue"), tenant.owner()), 200);
        String current = path(tenant, create(tenant, free("Neu", "1", "100.00", "0")));
        json(write(patch(current + "/issue"), tenant.owner()), 200);
        JsonNode detail = json(get(overdue).session(tenant.owner().session()), 200);
        assertThat(detail.get("status").asText()).isEqualTo("ISSUED");          // stored state unchanged
        assertThat(detail.get("displayStatus").asText()).isEqualTo("OVERDUE");
        assertThat(detail.get("overdue").asBoolean()).isTrue();
        String list = base(tenant.business());
        assertThat(json(get(list).param("status", "OVERDUE").session(tenant.owner().session()), 200)).hasSize(1);
        assertThat(json(get(list).param("status", "ISSUED").session(tenant.owner().session()), 200)).hasSize(1);
        assertThat(json(get(list).param("search", "muster").session(tenant.owner().session()), 200)).hasSize(2);
        assertThat(json(get(list).param("search", "RE-0001").session(tenant.owner().session()), 200)).hasSize(1);
        // Paying in full ends the overdue state.
        pay(tenant, overdue, "100.00", 201);
        assertThat(json(get(list).param("status", "OVERDUE").session(tenant.owner().session()), 200)).isEmpty();
        assertThat(json(get(list).param("status", "PAID").session(tenant.owner().session()), 200)).hasSize(1);
        json(get(list).param("status", "LATE").session(tenant.owner().session()), 400);
    }

    @Test void acceptedQuotesConvertExactlyOnce() throws Exception {
        Tenant tenant = tenant();
        String quotes = "/api/businesses/" + tenant.business() + "/quotes";
        JsonNode quote = json(write(post(quotes), tenant.owner()).content("""
                {"customerId":"%s","issueDate":"2026-11-01","validUntil":"2026-12-01","introductionText":"Angebotstext",
                 "closingText":"Gruss","notes":"Angebot intern","items":[{"serviceId":"%s","quantity":"2"},%s]}
                """.formatted(tenant.customer(), tenant.service(), free("Reise", "1", "45.50", "0"))), 201);
        String quotePath = quotes + "/" + quote.get("id").asText();
        String convert = quotePath + "/invoice";
        json(write(post(convert), tenant.owner()), 409);                                     // draft quote
        json(write(patch(quotePath + "/send"), tenant.owner()), 200);
        json(write(post(convert), tenant.owner()), 409);                                     // sent, not accepted
        JsonNode accepted = json(write(patch(quotePath + "/accept"), tenant.owner()), 200);

        // Parallel double click: exactly one invoice; every other request learns which one.
        int parallel = 6;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<org.springframework.mock.web.MockHttpServletResponse>> results = new ArrayList<>();
        for (int index = 0; index < parallel; index++) {
            results.add(pool.submit(() -> { start.await(); return mvc.perform(write(post(convert), tenant.owner())).andReturn().getResponse(); }));
        }
        start.countDown();
        List<JsonNode> created = new ArrayList<>();
        Set<String> referenced = new HashSet<>();
        for (var future : results) {
            var response = future.get(60, TimeUnit.SECONDS);
            JsonNode body = JSON.readTree(response.getContentAsString());
            if (response.getStatus() == 201) created.add(body);
            else {
                assertThat(response.getStatus()).isEqualTo(409);
                if (body.hasNonNull("invoiceId")) referenced.add(body.get("invoiceId").asText());
            }
        }
        pool.shutdown();
        assertThat(created).hasSize(1);
        JsonNode invoice = created.getFirst();
        assertThat(referenced).allMatch(id -> id.equals(invoice.get("id").asText()));
        assertThat(json(write(post(convert), tenant.owner()), 409).get("invoiceId").asText()).isEqualTo(invoice.get("id").asText());
        assertThat(jdbc.queryForObject("select count(*) from invoices where quote_id = ?", Integer.class, UUID.fromString(quote.get("id").asText()))).isEqualTo(1);

        // Copied positions, amounts and texts; own number; draft; link in both directions; quote unchanged.
        assertThat(invoice.get("status").asText()).isEqualTo("DRAFT");
        assertThat(invoice.get("invoiceNumber").asText()).startsWith("RE-");
        assertThat(invoice.get("grandTotal").asText()).isEqualTo(accepted.get("grandTotal").asText());
        assertThat(invoice.get("items")).hasSize(2);
        assertThat(invoice.get("items").get(0).get("lineTotal").asText()).isEqualTo(accepted.get("items").get(0).get("lineTotal").asText());
        assertThat(invoice.get("introductionText").asText()).isEqualTo("Angebotstext");
        assertThat(invoice.get("notes").isNull()).isTrue();                                  // internal quote notes stay internal
        assertThat(invoice.get("quote").get("quoteNumber").asText()).isEqualTo(accepted.get("quoteNumber").asText());
        JsonNode after = json(get(quotePath).session(tenant.owner().session()), 200);
        assertThat(after.get("invoice").get("id").asText()).isEqualTo(invoice.get("id").asText());
        assertThat(after.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(after.get("updatedAt").asText()).isEqualTo(accepted.get("updatedAt").asText());
        assertThat(pdfText(mvc.perform(get(path(tenant, invoice) + "/pdf").session(tenant.owner().session())).andReturn()
                .getResponse().getContentAsByteArray())).contains(accepted.get("quoteNumber").asText());
    }

    @Test void concurrentCreationAssignsUniqueSequentialNumbers() throws Exception {
        Tenant tenant = tenant();
        int parallel = 8;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int index = 0; index < parallel; index++) {
            results.add(pool.submit(() -> {
                start.await();
                return mvc.perform(write(post(base(tenant.business())), tenant.owner())
                        .content(invoice(tenant.customer(), free("A", "1", "1.00", "0"), "2026-11-18", 30))).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        for (var result : results) assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo(201);
        pool.shutdown();
        List<String> numbers = jdbc.queryForList("select invoice_number from invoices where business_id = ? order by invoice_number",
                String.class, UUID.fromString(tenant.business()));
        assertThat(numbers).containsExactly("RE-0001", "RE-0002", "RE-0003", "RE-0004", "RE-0005", "RE-0006", "RE-0007", "RE-0008");
        // Numbers are per business.
        Tenant other = tenant();
        assertThat(create(other, free("A", "1", "1.00", "0")).get("invoiceNumber").asText()).isEqualTo("RE-0001");
    }

    @Test void foreignAndUnknownResourcesAreIndistinguishable() throws Exception {
        Tenant alice = tenant();
        String path = path(alice, create(alice, free("A", "1", "100.00", "0")));
        json(write(patch(path + "/issue"), alice.owner()), 200);
        JsonNode paid = pay(alice, path, "10.00", 201);
        String payment = paid.get("payments").get(0).get("id").asText();
        Tenant mallory = tenant();
        String id = path.substring(path.lastIndexOf('/') + 1);
        for (String target : new String[] {base(alice.business()) + "/" + id, base(mallory.business()) + "/" + id,
                base(mallory.business()) + "/" + UUID.randomUUID()}) {
            String body = json(get(target).session(mallory.owner().session()), 404).toString();
            assertThat(body).isEqualTo("{\"error\":\"Invoice not found\",\"fields\":{},\"invoiceId\":null}");
            assertThat(json(get(target + "/pdf").session(mallory.owner().session()), 404).toString()).isEqualTo(body);
            assertThat(json(write(patch(target + "/issue"), mallory.owner()), 404).toString()).isEqualTo(body);
            assertThat(json(write(patch(target + "/cancel"), mallory.owner()).content("{\"reason\":\"x\"}"), 404).toString()).isEqualTo(body);
            assertThat(json(write(post(target + "/payments"), mallory.owner()).content("""
                    {"paymentDate":"2026-11-18","amount":"1.00","method":"CASH"}"""), 404).toString()).isEqualTo(body);
            assertThat(json(write(patch(target + "/payments/" + payment + "/reverse"), mallory.owner()).content("{\"reason\":\"x\"}"), 404).toString()).isEqualTo(body);
            assertThat(json(write(delete(target), mallory.owner()), 404).toString()).isEqualTo(body);
        }
        // Foreign customers/services/quotes cannot be referenced.
        json(write(post(base(mallory.business())), mallory.owner()).content(invoice(alice.customer(), "", "2026-11-18", 30)), 400);
        assertThat(json(write(post(base(mallory.business())), mallory.owner()).content(invoice(mallory.customer(),
                "{\"serviceId\":\"" + alice.service() + "\",\"quantity\":\"1\"}", "2026-11-18", 30)), 400).get("fields").has("items[0].serviceId")).isTrue();
        assertThat(json(get(base(mallory.business())).session(mallory.owner().session()), 200)).isEmpty();
        assertThat(json(get(path).session(alice.owner().session()), 200).get("paidTotal").asText()).isEqualTo("10.00");
    }

    @Test void sessionAndCsrfAreRequired() throws Exception {
        Tenant tenant = tenant();
        String path = path(tenant, create(tenant, free("A", "1", "100.00", "0")));
        json(get(base(tenant.business())), 401);
        json(get(path + "/pdf"), 401);
        mvc.perform(post(base(tenant.business())).session(tenant.owner().session()).contentType("application/json")
                .content(invoice(tenant.customer(), "", "2026-11-18", 30))).andExpect(status().isForbidden());
        mvc.perform(patch(path + "/issue").session(tenant.owner().session())).andExpect(status().isForbidden());
        mvc.perform(post(path + "/payments").session(tenant.owner().session()).contentType("application/json")
                .content("{\"paymentDate\":\"2026-11-18\",\"amount\":\"1\",\"method\":\"CASH\"}")).andExpect(status().isForbidden());
        assertThat(json(get(path).session(tenant.owner().session()), 200).get("status").asText()).isEqualTo("DRAFT");
    }

    @Test void issuedPdfKeepsItsSnapshotsAndCarriesTheQrBill() throws Exception {
        Tenant tenant = tenant();
        String path = path(tenant, create(tenant, free("Konzept", "2", "500.00", "8.10")));
        String draft = pdfText(mvc.perform(get(path + "/pdf").session(tenant.owner().session())).andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Rechnung-RE-0001.pdf")))
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(draft).contains("NICHT ZUR ZAHLUNG VERWENDEN", "Studio Nord GmbH");
        assertThat(draft).doesNotContain("Mahnung erst nach");                                  // internal notes
        json(write(patch(path + "/issue"), tenant.owner()), 200);
        byte[] issued = mvc.perform(get(path + "/pdf").session(tenant.owner().session())).andReturn().getResponse().getContentAsByteArray();
        String text = pdfText(issued);
        assertThat(text).contains("Zahlteil", "Empfangsschein", "CH93 0076 2011 6238 5295 7", "1 081.00", "Rechnung RE-0001",
                "Seestrasse 12", "Muster AG", "Bahnhofstrasse 1", "CHF 1’081.00");

        // Later changes to the business, its bank account and the customer do not alter the issued invoice.
        json(write(put("/api/businesses/" + tenant.business() + "/settings"), tenant.owner()).content(SETTINGS
                .replace("Studio Nord GmbH", "Neuer Name AG").replace("Seestrasse 12", "Andere Gasse 9").replace("CH93 0076 2011 6238 5295 7", "")), 200);
        json(write(put("/api/businesses/" + tenant.business() + "/customers/" + tenant.customer()), tenant.owner()).content("""
                {"type":"COMPANY","displayName":"Umbenannt AG","street":"Weg 3","postalCode":"4000","city":"Basel","country":"CH"}"""), 200);
        String again = pdfText(mvc.perform(get(path + "/pdf").session(tenant.owner().session())).andReturn().getResponse().getContentAsByteArray());
        assertThat(again).isEqualTo(text);
        // A new draft uses the new settings, which no longer allow a QR-bill (no IBAN) — and says why.
        JsonNode next = create(tenant, free("A", "1", "10.00", "0"));
        assertThat(next.get("qrBill").get("available").asBoolean()).isFalse();
        assertThat(next.get("qrBill").get("problems").get(0).asText()).contains("keine IBAN");
        assertThat(pdfText(mvc.perform(get(path(tenant, next) + "/pdf").session(tenant.owner().session())).andReturn().getResponse()
                .getContentAsByteArray())).contains("Neuer Name AG", "Umbenannt AG").doesNotContain("Zahlteil");
    }

    @Test void upgradeFromV7AddsInvoicesWithoutTouchingExistingData() {
        String schema = "invoice_upgrade_test";
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("7").load().migrate();
            UUID business = UUID.randomUUID(); UUID customer = UUID.randomUUID(); UUID quote = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".businesses (id,name) values (?, 'Existing')", business);
            jdbc.update("insert into " + schema + ".customers (id,business_id,type,display_name,customer_number) values (?,?,'COMPANY','Kunde','K-0001')", customer, business);
            LocalDate day = LocalDate.of(2026, 11, 18);
            jdbc.update("insert into " + schema + ".quotes (id,business_id,customer_id,quote_number,issue_date,valid_until,currency,subtotal,tax_total,grand_total,sender_name,sender_country,customer_number,customer_name,customer_country)"
                    + " values (?,?,?,'ANG-0001',?,?,'CHF',10,0,10,'Existing','CH','K-0001','Kunde','CH')", quote, business, customer, day, day);
            var flyway = org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("8").load();
            flyway.migrate(); flyway.validate();
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("8");
            assertThat(jdbc.queryForObject("select quote_number from " + schema + ".quotes where id = ?", String.class, quote)).isEqualTo("ANG-0001");
            String insert = "insert into " + schema + ".invoices (id,business_id,customer_id,quote_id,invoice_number,issue_date,payment_term_days,due_date,currency,subtotal,tax_total,grand_total,customer_number,customer_name,customer_country,sender_name,sender_country)"
                    + " values (?,?,?,?,?,?,30,?,'CHF',10,0,10,'K-0001','Kunde','CH','Existing','CH')";
            jdbc.update(insert, UUID.randomUUID(), business, customer, quote, "RE-0001", day, day.plusDays(30));
            for (Object[] invalid : List.of(
                    new Object[] {UUID.randomUUID(), business, customer, quote, "RE-0002", day, day.plusDays(30)},      // second invoice for the quote
                    new Object[] {UUID.randomUUID(), business, customer, null, "RE-0001", day, day.plusDays(30)},       // duplicate number
                    new Object[] {UUID.randomUUID(), business, customer, null, "RE-0003", day, day.plusDays(29)},       // inconsistent due date
                    new Object[] {UUID.randomUUID(), business, customer, null, "INV-1", day, day.plusDays(30)})) {      // number format
                assertThatThrownBy(() -> jdbc.update(insert, invalid)).isInstanceOf(DataIntegrityViolationException.class);
            }
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }
}
