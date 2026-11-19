package digital.margraf.hustivoo_backend.expense;

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

@org.springframework.test.context.TestPropertySource(properties = "hustivoo.receipts.directory=${java.io.tmpdir}/hustivoo-expense-tests")
@AutoConfigureMockMvc
class ExpenseTests extends PostgresIntegrationTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PASSWORD = "ExamplePassword123!";
    static final String SETTINGS = """
            {"name":"Studio Nord GmbH","street":"Seestrasse 12","postalCode":"8002","city":"Zürich","country":"CH",
             "iban":"CH93 0076 2011 6238 5295 7","bankName":"Zürcher Kantonalbank","vatNumber":"CHE-116.281.710 MWST"}""";
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReceiptStorage storage;
    @Autowired org.flywaydb.core.Flyway flyway;
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

    JsonNode json(org.springframework.test.web.servlet.RequestBuilder request, int status) throws Exception {
        return JSON.readTree(mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
    }

    record Fixture(Browser owner,String business,String supplier,String category) { String base() {return "/api/businesses/"+business;} }
    Fixture fixture() throws Exception {
        Browser owner=login();String b=json(write(post("/api/businesses"),owner).content("{\"name\":\"Studio\"}"),201).get("id").asText();String base="/api/businesses/"+b;
        String supplier=json(write(post(base+"/suppliers"),owner).content("{\"name\":\"Supplier\",\"email\":\"hello@example.com\"}"),201).get("id").asText();
        String category=json(write(post(base+"/expense-categories"),owner).content("{\"name\":\"Software\"}"),201).get("id").asText();
        return new Fixture(owner,b,supplier,category);
    }
    String input(Fixture f) {return """
        {"supplierId":"%s","categoryId":"%s","description":"Softwareabo","expenseDate":"2026-01-10","dueDate":"2026-01-20","netAmount":"100.00","taxRate":"8.10","inputTax":"UNCHECKED"}
        """.formatted(f.supplier(),f.category());}
    JsonNode create(Fixture f) throws Exception {return json(write(post(f.base()+"/expenses"),f.owner()).content(input(f)),201);}
    String path(Fixture f,JsonNode expense) {return f.base()+"/expenses/"+expense.get("id").asText();}
    @Test void createReadEditAndRoundExactly() throws Exception {
        var f=fixture();var e=create(f);assertThat(e.get("grossAmount").asText()).isEqualTo("108.10");assertThat(e.get("inputTax").asText()).isEqualTo("UNCHECKED");
        assertThat(e.get("displayStatus").asText()).isEqualTo("OVERDUE");assertThat(e.get("currency").asText()).isEqualTo("CHF");
        json(get(path(f,e)).session(f.owner().session()),200);
        var updated=json(write(put(path(f,e)),f.owner()).content(input(f).replace("100.00","0.50")),200);
        assertThat(updated.get("taxAmount").asText()).isEqualTo("0.04");assertThat(updated.get("grossAmount").asText()).isEqualTo("0.54");
        assertThat(jdbc.queryForObject("select net_amount from expenses where id=?",BigDecimal.class,UUID.fromString(e.get("id").asText()))).isEqualByComparingTo("0.50");
    }
    @Test void taxOverridesAndNoTaxAndPrecisionValidation() throws Exception {
        var f=fixture();
        for(String body:List.of(input(f).replace("100.00","-1"),input(f).replace("100.00","1.001"),input(f).replace("8.10","100.01"),input(f).replace("100.00","99999999999999999.99"),input(f).replace("\"inputTax\"","\"taxAmount\":\"9.00\",\"inputTax\"")))
            json(write(post(f.base()+"/expenses"),f.owner()).content(body),400);
        var adjusted=json(write(post(f.base()+"/expenses"),f.owner()).content(input(f).replace("\"inputTax\"","\"taxAmount\":\"8.12\",\"inputTax\"")),201);
        assertThat(adjusted.get("grossAmount").asText()).isEqualTo("108.12");
        var none=json(write(post(f.base()+"/expenses"),f.owner()).content(input(f).replace("\"taxRate\":\"8.10\",","")),201);assertThat(none.get("taxAmount").asText()).isEqualTo("0.00");
        for(String flag:List.of("UNCHECKED","DEDUCTIBLE","NOT_DEDUCTIBLE","PARTIAL")) assertThat(json(write(post(f.base()+"/expenses"),f.owner()).content(input(f).replace("UNCHECKED",flag)),201).get("inputTax").asText()).isEqualTo(flag);
    }
    @Test void paymentCorrectionIsAuditedAndCancellationIsExclusive() throws Exception {
        var f=fixture();var e=create(f);String p=path(f,e);
        var paid=json(write(post(p+"/payments"),f.owner()).content("{\"paymentDate\":\"2026-01-15\",\"method\":\"CARD\"}"),201);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");String booking=paid.get("payments").get(0).get("id").asText();
        json(write(patch(p+"/cancel"),f.owner()).content("{\"reason\":\"Wrong\"}"),409);
        json(write(put(p),f.owner()).content(input(f)),409);mvc.perform(write(delete(p),f.owner())).andExpect(status().isConflict());
        json(write(patch(p+"/payments/"+booking+"/reverse"),f.owner()).content("{\"reason\":\"\"}"),400);
        var reversed=json(write(patch(p+"/payments/"+booking+"/reverse"),f.owner()).content("{\"reason\":\"Wrong payment date\"}"),200);
        assertThat(reversed.get("status").asText()).isEqualTo("UNPAID");assertThat(reversed.get("payments").get(0).get("reversalReason").asText()).isEqualTo("Wrong payment date");
        assertThat(reversed.get("payments").get(0).get("reversedBy").asText()).isEqualTo(f.owner().user().getId().toString());
        json(write(patch(p+"/payments/"+booking+"/reverse"),f.owner()).content("{\"reason\":\"Again\"}"),409);
        json(write(patch(p+"/cancel"),f.owner()).content("{\"reason\":\"Cancelled\"}"),200);
        json(write(post(p+"/payments"),f.owner()).content("{\"paymentDate\":\"2026-01-15\",\"method\":\"CARD\"}"),409);
    }
    @Test void deleteOnlyUnpaidWithoutReceiptsOrPaymentHistory() throws Exception {
        var f=fixture();var e=create(f);mvc.perform(write(delete(path(f,e)),f.owner())).andExpect(status().isNoContent());json(get(path(f,e)).session(f.owner().session()),404);
    }
    @Test void supplierAndCategorySnapshotsStayFrozen() throws Exception {
        var f=fixture();var e=create(f);
        json(write(put(f.base()+"/suppliers/"+f.supplier()),f.owner()).content("{\"name\":\"Renamed\"}"),200);
        json(write(put(f.base()+"/expense-categories/"+f.category()),f.owner()).content("{\"name\":\"Changed\"}"),200);
        var saved=json(write(put(path(f,e)),f.owner()).content(input(f)),200);
        assertThat(saved.get("supplier").get("name").asText()).isEqualTo("Supplier");assertThat(saved.get("categoryName").asText()).isEqualTo("Software");
        json(write(patch(f.base()+"/suppliers/"+f.supplier()+"/archive"),f.owner()),200);
        json(write(post(f.base()+"/expenses"),f.owner()).content(input(f)),400);
        var list=json(get(f.base()+"/suppliers").param("archived","true").param("search","RENAM").session(f.owner().session()),200);assertThat(list.size()).isEqualTo(1);
        json(write(post(f.base()+"/expense-categories/defaults"),f.owner()),200);json(write(post(f.base()+"/expense-categories/defaults"),f.owner()),200);
        assertThat(json(get(f.base()+"/expense-categories").session(f.owner().session()),200).size()).isEqualTo(11);
    }
    @Test void filtersTotalsAndMonthTotals() throws Exception {
        var f=fixture();create(f);
        for(var query:Map.of("search","software","supplierId",f.supplier(),"categoryId",f.category(),"status","OVERDUE","from","2026-01-01").entrySet())
            assertThat(json(get(f.base()+"/expenses").param(query.getKey(),query.getValue()).session(f.owner().session()),200).get("items").size()).isEqualTo(1);
        assertThat(json(get(f.base()+"/expenses").param("search","missing").session(f.owner().session()),200).get("items").size()).isZero();
        assertThat(json(get(f.base()+"/expenses").session(f.owner().session()),200).get("totals").get("CHF").asText()).isEqualTo("108.10");
    }
    byte[] image() throws Exception {var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(10,10,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",output);return output.toByteArray();}
    JsonNode upload(Fixture f,JsonNode e,byte[] bytes,String filename,int status) throws Exception {
        return json(multipart(path(f,e)+"/receipts").file(new org.springframework.mock.web.MockMultipartFile("file",filename,"application/octet-stream",bytes)).session(f.owner().session()).header("X-CSRF-TOKEN",f.owner().csrf()),status);
    }
    @Test void receiptsValidateContentAndSecureDownloadAndRemoval() throws Exception {
        var f=fixture();var e=create(f);var receipt=upload(f,e,image(),"../../evil.png",201);String id=receipt.get("id").asText();
        assertThat(receipt.get("filename").asText()).doesNotContain("/");assertThat(java.nio.file.Files.exists(storage.path(UUID.fromString(id)))).isTrue();
        mvc.perform(get(path(f,e)+"/receipts/"+id).session(f.owner().session())).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Security-Policy","sandbox; default-src 'none'; frame-ancestors 'self'"));
        upload(f,e,"<script>alert(1)</script>".getBytes(),"fake.png",400);
        upload(f,e,new byte[10*1024*1024+1],"huge.pdf",413);
        mvc.perform(write(delete(path(f,e)),f.owner())).andExpect(status().isConflict());
        mvc.perform(write(delete(path(f,e)+"/receipts/"+id),f.owner())).andExpect(status().isNoContent());
        assertThat(java.nio.file.Files.exists(storage.path(UUID.fromString(id)))).isFalse();
    }
    @Test void pdfIsRebuiltWithoutInteractiveContent() throws Exception {
        byte[] bytes;try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var output=new java.io.ByteArrayOutputStream()) {
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.getDocumentCatalog().setOpenAction(new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript("app.alert('bad')"));pdf.save(output);bytes=output.toByteArray();
        }
        var safe=storage.sanitize(bytes);try(var pdf=Loader.loadPDF(safe.bytes())) {assertThat(pdf.getDocumentCatalog().getOpenAction()).isNull();assertThat(pdf.getNumberOfPages()).isEqualTo(1);}
    }
    @Test void orphanCleanupOnlyDeletesOldUnreferencedStorageFiles() throws Exception {
        var f=fixture();var e=create(f);var receipt=upload(f,e,image(),"keep.png",201);
        var orphan=storage.path(UUID.randomUUID());java.nio.file.Files.write(orphan,image());java.nio.file.Files.setLastModifiedTime(orphan,java.nio.file.attribute.FileTime.from(java.time.Instant.now().minus(java.time.Duration.ofDays(2))));
        var keep=storage.path(UUID.fromString(receipt.get("id").asText()));java.nio.file.Files.setLastModifiedTime(keep,java.nio.file.attribute.FileTime.from(java.time.Instant.now().minus(java.time.Duration.ofDays(2))));
        storage.cleanup();assertThat(java.nio.file.Files.exists(orphan)).isFalse();assertThat(java.nio.file.Files.exists(keep)).isTrue();
    }
    @Test void tenantIsolationAndCsrfForEveryResource() throws Exception {
        var a=fixture();var b=fixture();var e=create(a);var receipt=upload(a,e,image(),"receipt.png",201);String id=e.get("id").asText();
        String expected=json(get(a.base()+"/expenses/"+UUID.randomUUID()).session(a.owner().session()),404).toString();
        assertThat(json(get(path(a,e)).session(b.owner().session()),404).toString()).isEqualTo(expected);
        assertThat(json(get(b.base()+"/expenses/"+id).session(b.owner().session()),404).toString()).isEqualTo(expected);
        json(write(post(b.base()+"/expenses"),b.owner()).content(input(a)),404);
        json(write(put(a.base()+"/suppliers/"+a.supplier()),b.owner()).content("{\"name\":\"Attack\"}"),404);
        json(write(patch(b.base()+"/expense-categories/"+a.category()+"/archive"),b.owner()),404);
        json(get(path(a,e)+"/receipts/"+receipt.get("id").asText()).session(b.owner().session()),404);
        json(get(a.base()+"/finance/overview").param("from","2026-01-01").param("to","2026-12-31").session(b.owner().session()),404);
        mvc.perform(get(a.base()+"/expenses")).andExpect(status().isUnauthorized());
        mvc.perform(post(a.base()+"/expenses").session(a.owner().session()).contentType("application/json").content(input(a))).andExpect(status().isForbidden());
        mvc.perform(multipart(path(a,e)+"/receipts").file(new org.springframework.mock.web.MockMultipartFile("file","x.png","image/png",image())).session(a.owner().session())).andExpect(status().isForbidden());
        mvc.perform(delete(path(a,e)+"/receipts/"+receipt.get("id").asText()).session(a.owner().session())).andExpect(status().isForbidden());
        for(String endpoint:List.of(path(a,e)+"/cancel",a.base()+"/suppliers/"+a.supplier()+"/archive",a.base()+"/expense-categories/"+a.category()+"/archive")) mvc.perform(patch(endpoint).session(a.owner().session()).contentType("application/json").content("{\"reason\":\"Test\"}")).andExpect(status().isForbidden());
        mvc.perform(post(path(a,e)+"/payments").session(a.owner().session()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test void dashboardSeparatesInvoicesAndActualPaymentsAndExpenses() throws Exception {
        var f=fixture();var e=create(f);json(write(post(path(f,e)+"/payments"),f.owner()).content("{\"paymentDate\":\"2026-02-01\",\"method\":\"BANK_TRANSFER\"}"),201);
        json(write(put(f.base()+"/settings"),f.owner()).content(SETTINGS),200);
        var customer=json(write(post(f.base()+"/customers"),f.owner()).content("{\"type\":\"COMPANY\",\"displayName\":\"Client\",\"street\":\"Street 1\",\"postalCode\":\"8000\",\"city\":\"Zürich\",\"country\":\"CH\"}"),201);
        String input="""
            {"customerId":"%s","issueDate":"2026-01-10","paymentTermDays":30,"items":[{"description":"Work","quantity":"1","unit":"HOUR","unitPrice":"200.00","taxRate":"0"}]}
            """.formatted(customer.get("id").asText());
        var invoice=json(write(post(f.base()+"/invoices"),f.owner()).content(input),201);String path=f.base()+"/invoices/"+invoice.get("id").asText();
        json(write(patch(path+"/issue"),f.owner()),200);
        var payment=json(write(post(path+"/payments"),f.owner()).content("{\"paymentDate\":\"2026-02-01\",\"amount\":\"50.00\",\"method\":\"BANK_TRANSFER\"}"),201);
        var dashboard=json(get(f.base()+"/finance/overview").param("from","2026-01-01").param("to","2026-12-31").session(f.owner().session()),200);
        assertThat(dashboard.get("totals").get("invoiced").asText()).isEqualTo("200.00");assertThat(dashboard.get("totals").get("received").asText()).isEqualTo("50.00");assertThat(dashboard.get("totals").get("outstanding").asText()).isEqualTo("150.00");
        assertThat(dashboard.get("totals").get("paidExpenses").asText()).isEqualTo("108.10");assertThat(dashboard.get("months").get(1).get("received").asText()).isEqualTo("50.00");
        String paymentId=payment.get("payments").get(0).get("id").asText();json(write(patch(path+"/payments/"+paymentId+"/reverse"),f.owner()).content("{\"reason\":\"Mistake\"}"),200);
        json(write(patch(path+"/cancel"),f.owner()).content("{\"reason\":\"Cancelled\"}"),200);
        dashboard=json(get(f.base()+"/finance/overview").param("from","2026-01-01").param("to","2026-12-31").session(f.owner().session()),200);
        assertThat(dashboard.get("totals").get("invoiced").asText()).isEqualTo("0.00");assertThat(dashboard.get("totals").get("received").asText()).isEqualTo("0.00");
        var paidExpense=json(get(path(f,e)).session(f.owner().session()),200);
        json(write(patch(path(f,e)+"/payments/"+paidExpense.get("payments").get(0).get("id").asText()+"/reverse"),f.owner()).content("{\"reason\":\"Correction\"}"),200);
        json(write(patch(path(f,e)+"/cancel"),f.owner()).content("{\"reason\":\"Duplicate expense\"}"),200);
        dashboard=json(get(f.base()+"/finance/overview").param("from","2026-01-01").param("to","2026-12-31").session(f.owner().session()),200);
        assertThat(dashboard.get("totals").get("expenses").asText()).isEqualTo("0.00");assertThat(dashboard.get("totals").get("paidExpenses").asText()).isEqualTo("0.00");
    }
    @Test void flywayUpgradeFromV8PreservesExistingBusinesses() {
        String schema="expense_upgrade_test";
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("8").load().migrate();
            UUID business=UUID.randomUUID();jdbc.update("insert into "+schema+".businesses(id,name) values(?,?)",business,"Existing");
            var upgrade=org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load();upgrade.migrate();upgrade.validate();
            assertThat(upgrade.info().current().getVersion().toString()).isEqualTo("9");assertThat(jdbc.queryForObject("select name from "+schema+".businesses where id=?",String.class,business)).isEqualTo("Existing");
            assertThat(upgrade.info().pending()).isEmpty();flyway.validate();
        } finally {jdbc.execute("drop schema if exists "+schema+" cascade");}
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired ReceiptService receiptService;
    @Test void rolledBackUploadRemovesItsFileAndMetadata() throws Exception {
        var f=fixture();var e=create(f);UUID[] stored=new UUID[1];byte[] bytes=image();
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(f.owner().user().getEmail(),null,java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                try {var receipt=receiptService.upload(UUID.fromString(f.business()),UUID.fromString(e.get("id").asText()),new org.springframework.mock.web.MockMultipartFile("file","rollback.png","image/png",bytes));stored[0]=UUID.fromString(receipt.get("id").toString());status.setRollbackOnly();}
                catch(java.io.IOException exception) {throw new java.io.UncheckedIOException(exception);}
            });
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        assertThat(java.nio.file.Files.exists(storage.path(stored[0]))).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from expense_receipts where id=?",Integer.class,stored[0])).isZero();
    }
    @Test void memberMayManageExpensesAndParallelPaymentIsSingle() throws Exception {
        var f=fixture();var member=login();
        jdbc.update("insert into memberships(id,user_id,business_id,role) values(?,?,?,'MEMBER')",UUID.randomUUID(),member.user().getId(),UUID.fromString(f.business()));
        var e=json(write(post(f.base()+"/expenses"),member).content(input(f)),201);String p=path(f,e);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> pay=()->mvc.perform(write(post(p+"/payments"),member).content("{\"paymentDate\":\"2026-01-15\",\"method\":\"CARD\"}")).andReturn().getResponse().getStatus();
            var one=executor.submit(pay);var two=executor.submit(pay);
            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } finally {executor.shutdownNow();}
        assertThat(jdbc.queryForObject("select count(*) from expense_payments where expense_id=?",Integer.class,UUID.fromString(e.get("id").asText()))).isEqualTo(1);
    }
}
