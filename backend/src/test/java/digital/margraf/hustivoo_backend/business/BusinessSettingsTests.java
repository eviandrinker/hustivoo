package digital.margraf.hustivoo_backend.business;

import digital.margraf.hustivoo_backend.membership.Membership;
import digital.margraf.hustivoo_backend.membership.MembershipRepository;
import digital.margraf.hustivoo_backend.membership.MembershipRole;
import digital.margraf.hustivoo_backend.support.PostgresIntegrationTest;
import digital.margraf.hustivoo_backend.user.User;
import digital.margraf.hustivoo_backend.user.UserRepository;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
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
class BusinessSettingsTests extends PostgresIntegrationTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PASSWORD = "ExamplePassword123!";
    // Valid examples: IBAN from the Swiss IBAN specification, UID of the Swiss Federal Chancellery.
    static final String SETTINGS = """
            {"name":"  Studio Nord GmbH ","legalForm":"GmbH","industry":"Design & Kreatives",
             "street":"Seestrasse 12","postalCode":"8002","city":"Zürich","country":"CH",
             "email":"hallo@studio-nord.ch","phone":"+41 44 123 45 67","website":"www.studio-nord.ch",
             "vatNumber":"che 116.281.710 mwst","iban":"ch93 0076 2011 6238 5295 7","bankName":"Zürcher Kantonalbank",
             "documentFooter":"Zahlbar innert 30 Tagen netto.","brandColor":"#1f3a5f"}
            """;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BusinessRepository businesses;
    @Autowired MembershipRepository memberships;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired javax.sql.DataSource dataSource;

    record Browser(User user, MockHttpSession session, String csrf) {}

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

    JsonNode json(MockHttpServletRequestBuilder request, int status) throws Exception {
        return JSON.readTree(mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
    }

    String business(Browser owner, String name) throws Exception {
        return json(write(post("/api/businesses"), owner).content("{\"name\":\"" + name + "\",\"currency\":\"CHF\"}"), 201).get("id").asText();
    }

    Browser member(String business) throws Exception {
        Browser member = login();
        memberships.saveAndFlush(new Membership(member.user(), businesses.findById(UUID.fromString(business)).orElseThrow(), MembershipRole.MEMBER));
        return member;
    }

    static String settings(String business) { return "/api/businesses/" + business + "/settings"; }

    MockHttpServletRequestBuilder upload(String business, Browser browser, byte[] content, String type) {
        return put(settings(business) + "/logo").session(browser.session()).header("X-CSRF-TOKEN", browser.csrf())
                .contentType(type).content(content);
    }

    static byte[] image(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(0x1F3A5F)); graphics.fillRect(0, 0, width, height / 2); graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    /** A tiny valid PNG whose header claims a huge canvas (a "decompression bomb"). */
    static byte[] bomb() throws Exception {
        byte[] png = image(16, 16, "png");
        ByteBuffer buffer = ByteBuffer.wrap(png);
        buffer.putInt(16, 20_000).putInt(20, 20_000);  // IHDR width/height
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);                         // chunk type + data
        buffer.putInt(29, (int) crc.getValue());
        return png;
    }

    static String pdf(byte[] bytes) throws Exception {
        try (var document = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(document); }
    }

    // ---------------------------------------------------------------- tests

    @Test void ownerReadsAndUpdatesNormalisedSettings() throws Exception {
        Browser owner = login();
        String business = business(owner, "Studio Nord");
        JsonNode initial = json(get(settings(business)).session(owner.session()), 200);
        assertThat(initial.get("name").asText()).isEqualTo("Studio Nord");
        assertThat(initial.get("role").asText()).isEqualTo("OWNER");
        assertThat(initial.get("editable").asBoolean()).isTrue();
        assertThat(initial.get("iban").isNull()).isTrue();
        assertThat(initial.get("logo").isNull()).isTrue();

        // Client-supplied identity, role and currency are ignored.
        String body = SETTINGS.replace("\"name\"", "\"id\":\"" + UUID.randomUUID() + "\",\"role\":\"OWNER\",\"currency\":\"EUR\",\"name\"");
        JsonNode updated = json(write(put(settings(business)), owner).content(body), 200);
        assertThat(updated.get("id").asText()).isEqualTo(business);
        assertThat(updated.get("name").asText()).isEqualTo("Studio Nord GmbH");
        assertThat(updated.get("currency").asText()).isEqualTo("CHF");
        assertThat(updated.get("iban").asText()).isEqualTo("CH9300762011623852957");
        assertThat(updated.get("vatNumber").asText()).isEqualTo("CHE-116.281.710 MWST");
        assertThat(updated.get("brandColor").asText()).isEqualTo("#1F3A5F");
        assertThat(updated.get("website").asText()).isEqualTo("www.studio-nord.ch");
        assertThat(json(get(settings(business)).session(owner.session()), 200)).isEqualTo(updated);
        assertThat(json(get("/api/businesses/" + business).session(owner.session()), 200).get("name").asText()).isEqualTo("Studio Nord GmbH");

        // Optional fields can be cleared again; blank strings become null.
        JsonNode cleared = json(write(put(settings(business)), owner).content("""
                {"name":"Studio Nord GmbH","country":"DE","iban":"  ","brandColor":"","documentFooter":null}"""), 200);
        assertThat(cleared.get("iban").isNull()).isTrue();
        assertThat(cleared.get("brandColor").isNull()).isTrue();
        assertThat(cleared.get("street").isNull()).isTrue();
        assertThat(cleared.get("country").asText()).isEqualTo("DE");
    }

    @Test void invalidValuesAreRejectedPerFieldWithoutChanges() throws Exception {
        Browser owner = login();
        String business = business(owner, "Studio Nord");
        json(write(put(settings(business)), owner).content(SETTINGS), 200);
        Map<String, String> invalid = Map.ofEntries(
                Map.entry("name", "\"name\":\"  \""),
                Map.entry("country", "\"country\":\"XX\""),
                Map.entry("email", "\"email\":\"keine-mail\""),
                Map.entry("phone", "\"phone\":\"12\""),
                Map.entry("website", "\"website\":\"javascript:alert(1)\""),
                Map.entry("vatNumber", "\"vatNumber\":\"CHE-116.281.711\""),
                Map.entry("iban", "\"iban\":\"CH93 0076 2011 6238 5295 8\""),
                Map.entry("postalCode", "\"postalCode\":\"<8002>\""),
                Map.entry("documentFooter", "\"documentFooter\":\"Zeile 1\\nZeile 2\""),
                Map.entry("brandColor", "\"brandColor\":\"#F5F5F5\""));
        for (var entry : invalid.entrySet()) {
            String field = entry.getKey();
            String body = SETTINGS.replaceFirst("\"" + field + "\":(\"[^\"]*\"|null)", java.util.regex.Matcher.quoteReplacement(entry.getValue()));
            assertThat(body).as(field).contains(entry.getValue());
            JsonNode error = json(write(put(settings(business)), owner).content(body), 400);
            assertThat(error.get("error").asText()).isEqualTo("Invalid request");
            assertThat(error.get("fields").has(field)).as(field).isTrue();
            assertThat(error.toString()).doesNotContain("alert(1)", "keine-mail");  // submitted values are never echoed
        }
        json(write(put(settings(business)), owner).content("{\"name\":\"" + "x".repeat(256) + "\",\"country\":\"CH\"}"), 400);
        json(write(put(settings(business)), owner).content("{\"name\":\"A\",\"country\":\"CH\",\"documentFooter\":\"" + "x".repeat(161) + "\"}"), 400);
        json(write(put(settings(business)), owner).content("not json"), 400);
        assertThat(json(get(settings(business)).session(owner.session()), 200).get("iban").asText()).isEqualTo("CH9300762011623852957");
        // Database constraints back the request validation.
        assertThatThrownBy(() -> jdbc.update("update businesses set iban = 'ch93 x' where id = ?", UUID.fromString(business)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void membersReadButOnlyOwnersChangeSettings() throws Exception {
        Browser owner = login();
        String business = business(owner, "Studio Nord");
        json(write(put(settings(business)), owner).content(SETTINGS), 200);
        json(upload(business, owner, image(200, 80, "png"), "image/png"), 200);
        Browser member = member(business);

        JsonNode read = json(get(settings(business)).session(member.session()), 200);
        assertThat(read.get("role").asText()).isEqualTo("MEMBER");
        assertThat(read.get("editable").asBoolean()).isFalse();
        assertThat(read.get("iban").asText()).isEqualTo("CH9300762011623852957");
        mvc.perform(get(settings(business) + "/logo").session(member.session())).andExpect(status().isOk());

        for (MockHttpServletRequestBuilder change : new MockHttpServletRequestBuilder[] {
                write(put(settings(business)), member).content(SETTINGS.replace("Studio Nord GmbH", "Übernommen")),
                upload(business, member, image(200, 80, "png"), "image/png"),
                write(delete(settings(business) + "/logo"), member)}) {
            JsonNode error = json(change, 403);
            assertThat(error.get("error").asText()).isEqualTo("Only owners can change business settings");
        }
        JsonNode unchanged = json(get(settings(business)).session(owner.session()), 200);
        assertThat(unchanged.get("name").asText()).isEqualTo("Studio Nord GmbH");
        assertThat(unchanged.get("logo").isNull()).isFalse();
    }

    @Test void foreignAndUnknownBusinessesAreIndistinguishable() throws Exception {
        Browser alice = login();
        String business = business(alice, "Alice AG");
        json(upload(business, alice, image(64, 64, "png"), "image/png"), 200);
        Browser mallory = login();
        business(mallory, "Mallory GmbH");
        for (String target : new String[] {business, UUID.randomUUID().toString()}) {
            String body = json(get(settings(target)).session(mallory.session()), 404).toString();
            assertThat(body).isEqualTo("{\"error\":\"Business not found\",\"fields\":{}}");
            assertThat(json(write(put(settings(target)), mallory).content(SETTINGS), 404).toString()).isEqualTo(body);
            assertThat(json(get(settings(target) + "/logo").session(mallory.session()), 404).toString()).isEqualTo(body);
            assertThat(json(upload(target, mallory, image(64, 64, "png"), "image/png"), 404).toString()).isEqualTo(body);
            assertThat(json(write(delete(settings(target) + "/logo"), mallory), 404).toString()).isEqualTo(body);
        }
        assertThat(json(get(settings(business)).session(alice.session()), 200).get("name").asText()).isEqualTo("Alice AG");
        json(get(settings("not-a-uuid")).session(alice.session()), 400);
    }

    @Test void sessionAndCsrfAreRequired() throws Exception {
        Browser owner = login();
        String business = business(owner, "Studio Nord");
        json(get(settings(business)), 401);
        mvc.perform(put(settings(business)).session(owner.session()).contentType("application/json").content(SETTINGS))
                .andExpect(status().isForbidden());
        mvc.perform(put(settings(business) + "/logo").session(owner.session()).contentType("image/png").content(image(64, 64, "png")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(settings(business) + "/logo").session(owner.session())).andExpect(status().isForbidden());
        assertThat(json(get(settings(business)).session(owner.session()), 200).get("street").isNull()).isTrue();
    }

    @Test void logosAreValidatedNormalisedAndServedPrivately() throws Exception {
        Browser owner = login();
        String business = business(owner, "Studio Nord");
        String path = settings(business) + "/logo";
        json(get(path).session(owner.session()), 404);

        JsonNode jpeg = json(upload(business, owner, image(2400, 600, "jpeg"), "image/jpeg"), 200).get("logo");
        assertThat(jpeg.get("width").asInt()).isEqualTo(1200);   // scaled into 1200 × 600
        assertThat(jpeg.get("height").asInt()).isEqualTo(300);
        var response = mvc.perform(get(path).session(owner.session())).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("private"), org.hamcrest.Matchers.containsString("no-cache"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse();
        byte[] stored = response.getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(stored)).getWidth()).isEqualTo(1200);
        assertThat(stored).startsWith(0x89, 'P', 'N', 'G');     // re-encoded: no JPEG data or metadata survives
        mvc.perform(get(path).session(owner.session()).header("If-None-Match", response.getHeader("ETag")))
                .andExpect(status().isNotModified());

        // Rejected: wrong signature, mismatching type, SVG, too small, decompression bomb, too large, empty.
        byte[] png = image(64, 64, "png");
        record Case(byte[] content, String type, String message) {}
        for (Case invalid : new Case[] {
                new Case("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(), "image/svg+xml", "PNG- oder JPEG"),
                new Case(png, "image/jpeg", "PNG- oder JPEG"),
                new Case("GIF89a".getBytes(), "image/png", "PNG- oder JPEG"),
                new Case(image(8, 8, "png"), "image/png", "16 × 16"),
                new Case(bomb(), "image/png", "40 Megapixel"),
                new Case(new byte[LogoImages.MAX_UPLOAD_BYTES + 1], "image/png", "2 MB"),
                new Case(new byte[0], "image/png", "gelesen")}) {
            JsonNode error = json(upload(business, owner, invalid.content(), invalid.type()), 400);
            assertThat(error.get("error").asText()).isEqualTo("Invalid logo");
            assertThat(error.get("fields").get("logo").asText()).contains(invalid.message());
        }
        assertThat(json(get(settings(business)).session(owner.session()), 200).get("logo").get("id").asText())
                .isEqualTo(jpeg.get("id").asText());

        // Replacing deletes the unused previous logo; removing clears it.
        JsonNode replaced = json(upload(business, owner, png, "image/png"), 200).get("logo");
        assertThat(replaced.get("id").asText()).isNotEqualTo(jpeg.get("id").asText());
        assertThat(jdbc.queryForObject("select count(*) from business_logos where business_id = ?", Integer.class, UUID.fromString(business))).isEqualTo(1);
        assertThat(json(write(delete(path), owner), 200).get("logo").isNull()).isTrue();
        json(get(path).session(owner.session()), 404);
        assertThat(jdbc.queryForObject("select count(*) from business_logos where business_id = ?", Integer.class, UUID.fromString(business))).isZero();
    }

    @Test void logosStayWithinTheirBusinessAndAreImmutable() throws Exception {
        Browser alice = login();
        String first = business(alice, "Alice AG");
        String second = business(alice, "Alice Zwei");
        UUID logo = UUID.fromString(json(upload(first, alice, image(64, 64, "png"), "image/png"), 200).get("logo").get("id").asText());
        assertThatThrownBy(() -> jdbc.update("update businesses set logo_id = ? where id = ?", logo, UUID.fromString(second)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update business_logos set content = '\\x00'::bytea where id = ?", logo))
                .isInstanceOf(DataIntegrityViolationException.class);
        json(get(settings(second) + "/logo").session(alice.session()), 404);
    }

    @Test void quotesUseCurrentSettingsWhileDraftingAndFreezeThemWhenSent() throws Exception {
        Browser owner = login();
        String business = business(owner, "Studio Nord");
        json(write(put(settings(business)), owner).content(SETTINGS), 200);
        UUID firstLogo = UUID.fromString(json(upload(business, owner, image(300, 90, "png"), "image/png"), 200).get("logo").get("id").asText());
        String customer = json(write(post("/api/businesses/" + business + "/customers"), owner).content("""
                {"type":"COMPANY","displayName":"Muster AG","street":"Bahnhofstrasse 1","postalCode":"8001","city":"Bern","country":"CH"}"""), 201).get("id").asText();
        String quotes = "/api/businesses/" + business + "/quotes";
        String body = """
                {"customerId":"%s","issueDate":"2026-11-18","validUntil":"2026-12-18",
                 "items":[{"description":"Konzept","quantity":"1","unit":"PIECE","unitPrice":"100.00","taxRate":"8.10"}]}""".formatted(customer);
        String sent = quotes + "/" + json(write(post(quotes), owner).content(body), 201).get("id").asText();
        String draft = quotes + "/" + json(write(post(quotes), owner).content(body), 201).get("id").asText();

        String draftText = pdf(mvc.perform(get(sent + "/pdf").session(owner.session())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(draftText).contains("Studio Nord GmbH", "Seestrasse 12", "8002 Zürich", "hallo@studio-nord.ch", "+41 44 123 45 67",
                "www.studio-nord.ch", "UID CHE-116.281.710 MWST", "IBAN CH93 0076 2011 6238 5295 7", "Zürcher Kantonalbank",
                "Zahlbar innert 30 Tagen netto.", "CHF 108.10");
        json(write(patch(sent + "/send"), owner), 200);
        assertThat(jdbc.queryForObject("select sender_logo_id from quotes where id = ?", UUID.class,
                UUID.fromString(sent.substring(sent.lastIndexOf('/') + 1)))).isEqualTo(firstLogo);

        // Settings and logo change after sending.
        json(write(put(settings(business)), owner).content(SETTINGS.replace("Seestrasse 12", "Neugasse 7")
                .replace("ch93 0076 2011 6238 5295 7", "").replace("Studio Nord GmbH", "Studio Süd AG")), 200);
        json(upload(business, owner, image(120, 120, "png"), "image/png"), 200);
        assertThat(jdbc.queryForObject("select count(*) from business_logos where id = ?", Integer.class, firstLogo)).isEqualTo(1);

        byte[] frozen = mvc.perform(get(sent + "/pdf").session(owner.session())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(pdf(frozen)).contains("Studio Nord GmbH", "Seestrasse 12", "IBAN CH93 0076 2011 6238 5295 7")
                .doesNotContain("Neugasse", "Studio Süd");
        String current = pdf(mvc.perform(get(draft + "/pdf").session(owner.session())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(current).contains("Studio Süd AG", "Neugasse 7").doesNotContain("IBAN", "Seestrasse");

        // Removing the logo keeps the one frozen into the sent quote.
        json(write(delete(settings(business) + "/logo"), owner), 200);
        assertThat(jdbc.queryForObject("select count(*) from business_logos where id = ?", Integer.class, firstLogo)).isEqualTo(1);
        byte[] again = mvc.perform(get(sent + "/pdf").session(owner.session())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(pdf(again)).isEqualTo(pdf(frozen));  // PDFs embed a creation date, so compare their text
        try (var document = Loader.loadPDF(again)) {
            var resources = document.getPage(0).getResources();
            assertThat(resources.getXObjectNames()).isNotEmpty();  // the frozen logo is still drawn
        }
    }

    @Test void upgradeFromV6FreezesTheSenderCountryOfExistingQuotes() {
        String schema = "settings_upgrade_test";
        try {
            org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("6").load().migrate();
            UUID business = UUID.randomUUID(); UUID customer = UUID.randomUUID(); UUID quote = UUID.randomUUID();
            jdbc.update("insert into " + schema + ".businesses (id,name,country) values (?, 'Existing', 'DE')", business);
            jdbc.update("insert into " + schema + ".customers (id,business_id,type,display_name,customer_number) values (?,?,'COMPANY','Kunde','K-0001')", customer, business);
            LocalDate day = LocalDate.of(2026, 11, 18);
            jdbc.update("insert into " + schema + ".quotes (id,business_id,customer_id,quote_number,issue_date,valid_until,currency,subtotal,tax_total,grand_total,sender_name,customer_number,customer_name,customer_country)"
                    + " values (?,?,?,'ANG-0001',?,?,'CHF',?,?,?,'Existing','K-0001','Kunde','CH')",
                    quote, business, customer, day, day, new BigDecimal("10.00"), BigDecimal.ZERO.setScale(2), new BigDecimal("10.00"));
            jdbc.update("update " + schema + ".quotes set status = 'SENT', sent_at = now() where id = ?", quote);

            var flyway = org.flywaydb.core.Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("7").load();
            flyway.migrate(); flyway.validate();
            assertThat(flyway.info().current().getVersion().toString()).isEqualTo("7");
            assertThat(jdbc.queryForObject("select sender_country from " + schema + ".quotes where id = ?", String.class, quote)).isEqualTo("DE");
            assertThat(jdbc.queryForObject("select sender_street from " + schema + ".quotes where id = ?", String.class, quote)).isNull();
            assertThat(jdbc.queryForObject("select name from " + schema + ".businesses where id = ?", String.class, business)).isEqualTo("Existing");
            // The issued-quote guard is active again after the backfill.
            assertThatThrownBy(() -> jdbc.update("update " + schema + ".quotes set sender_street = 'x' where id = ?", quote))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally { jdbc.execute("drop schema if exists " + schema + " cascade"); }
    }
}
