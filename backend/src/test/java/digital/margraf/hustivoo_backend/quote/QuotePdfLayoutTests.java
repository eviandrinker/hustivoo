package digital.margraf.hustivoo_backend.quote;

import digital.margraf.hustivoo_backend.business.BusinessResponse;
import digital.margraf.hustivoo_backend.customer.Customer;
import digital.margraf.hustivoo_backend.customer.CustomerRequest;
import digital.margraf.hustivoo_backend.customer.CustomerType;
import digital.margraf.hustivoo_backend.document.DocumentSender;
import digital.margraf.hustivoo_backend.document.DocumentTheme;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/** Layout regression tests on real PDFs: geometry from glyph positions, text from extraction. */
class QuotePdfLayoutTests {
    static final QuotePdfRenderer RENDERER = new QuotePdfRenderer();
    static final BusinessResponse BUSINESS = new BusinessResponse(UUID.randomUUID(), "Live-Name (darf nicht erscheinen)", null, null, "CH", "CHF", Instant.now(), Instant.now());

    // ------------------------------------------------------------------ fixtures

    record ItemSpec(String description, String quantity, ServiceUnit unit, String price, String rate) {}

    static ItemSpec item(int number) {
        return new ItemSpec("Position %02d Konzept und Umsetzung\nAbstimmung, Entwurf und zwei Korrekturschleifen".formatted(number), "1", ServiceUnit.PIECE, "10.00", "8.10");
    }

    static Quote quote(String sender, Customer customer, List<ItemSpec> specs, Consumer<Quote> header) {
        Quote quote = new Quote(UUID.randomUUID(), "ANG-0042");
        quote.editHeader(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 12, 18), "CHF",
                "Vielen Dank für Ihre Anfrage. Gerne unterbreiten wir Ihnen das folgende Angebot.",
                "Vielen Dank für Ihr Vertrauen. Wir freuen uns auf die Zusammenarbeit.", "INTERNE NOTIZ – nie im PDF");
        if (header != null) header.accept(quote);
        quote.snapshot(sender, customer);
        List<QuoteItem> items = new ArrayList<>();
        List<QuoteCalculator.Line> lines = new ArrayList<>();
        for (ItemSpec spec : specs) {
            var line = QuoteCalculator.line(new BigDecimal(spec.quantity()), new BigDecimal(spec.price()), new BigDecimal(spec.rate()), "CHF");
            lines.add(line);
            items.add(new QuoteItem(quote, items.size() + 1, null, spec.description(), new BigDecimal(spec.quantity()), spec.unit(),
                    new BigDecimal(spec.price()), new BigDecimal(spec.rate()), line));
        }
        quote.replaceItems(items, QuoteCalculator.totals(lines));
        return quote;
    }

    static Customer customer(String name, String company, String street, String city) {
        return new Customer(UUID.randomUUID(), new CustomerRequest(CustomerType.COMPANY, name, company, "Erika", "Muster",
                null, null, street, "8001", city, "CH", null), "K-0007");
    }

    static Quote standard(int count) {
        return quote("Studio Nord", customer("Muster AG", "Muster AG", "Bahnhofstrasse 1", "Zürich"),
                java.util.stream.IntStream.rangeClosed(1, count).mapToObj(QuotePdfLayoutTests::item).toList(), null);
    }

    // ------------------------------------------------------------------ PDF inspection

    record Glyph(int page, String text, float x, float baseline, float width, float size) {
        float top() { return baseline + size * 0.72f; }
        float bottom() { return baseline - size * 0.2f; }
    }

    record Pdf(byte[] bytes, List<String> pages, List<List<Glyph>> glyphs, PDDocument document) {
        String text() { return String.join("\n", pages); }
        String compact() { return text().replaceAll("\\s+", ""); }
    }

    static Pdf inspect(byte[] bytes) throws IOException {
        dump(bytes);
        PDDocument document = Loader.loadPDF(bytes);
        List<String> pages = new ArrayList<>();
        List<List<Glyph>> glyphs = new ArrayList<>();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            int index = page;
            List<Glyph> collected = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override protected void writeString(String text, List<TextPosition> positions) throws IOException {
                    for (TextPosition position : positions) {
                        if (position.getUnicode().isBlank()) continue;
                        collected.add(new Glyph(index, position.getUnicode(), position.getXDirAdj(),
                                position.getPageHeight() - position.getYDirAdj(), position.getWidthDirAdj(), position.getFontSizeInPt()));
                    }
                    super.writeString(text, positions);
                }
            };
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            pages.add(stripper.getText(document));
            glyphs.add(collected);
        }
        return new Pdf(bytes, pages, glyphs, document);
    }

    /** Optional visual review: -Dquote.pdf.out=/some/dir writes every rendered PDF there. */
    static void dump(byte[] bytes) throws IOException {
        String directory = System.getProperty("quote.pdf.out");
        if (directory != null) java.nio.file.Files.write(java.nio.file.Path.of(directory, "quote-" + System.nanoTime() + ".pdf"), bytes);
    }

    static void assertLayout(Pdf pdf) {
        for (List<Glyph> page : pdf.glyphs()) {
            assertThat(page).as("no empty page").anySatisfy(glyph ->
                    assertThat(glyph.baseline()).isBetween(QuotePdfRenderer.CONTENT_BOTTOM, QuotePdfRenderer.TOP - 20));
            for (Glyph glyph : page) {
                assertThat(glyph.x()).as("left margin: %s", glyph).isGreaterThanOrEqualTo(QuotePdfRenderer.LEFT - 0.5f);
                assertThat(glyph.x() + glyph.width()).as("right margin: %s", glyph).isLessThanOrEqualTo(QuotePdfRenderer.RIGHT + 0.5f);
                assertThat(glyph.baseline() < QuotePdfRenderer.FOOTER_RULE - 8 || glyph.baseline() >= QuotePdfRenderer.CONTENT_BOTTOM)
                        .as("content must not enter the footer zone: %s", glyph).isTrue();
            }
            for (int a = 0; a < page.size(); a++) {
                Glyph first = page.get(a);
                for (int b = a + 1; b < page.size(); b++) {
                    Glyph second = page.get(b);
                    if (Math.abs(first.baseline() - second.baseline()) < 0.5f) continue; // same line
                    float width = Math.min(first.x() + first.width(), second.x() + second.width()) - Math.max(first.x(), second.x());
                    float height = Math.min(first.top(), second.top()) - Math.max(first.bottom(), second.bottom());
                    assertThat(width > 0.5f && height > 0.5f).as("overlap: %s / %s", first, second).isFalse();
                }
            }
        }
        for (int page = 0; page < pdf.pages().size(); page++) {
            String text = pdf.pages().get(page);
            assertThat(text).contains("Seite " + (page + 1) + " von " + pdf.pages().size());
            // A table header always has at least one position (or the carry-over plus a position) below it.
            if (text.replaceAll("\\s+", "").contains("BESCHREIBUNG") && pdf.compact().contains("Position01")) {
                assertThat(text).as("orphaned table header on page %d", page + 1).containsPattern("Position \\d{2}");
            }
        }
    }

    static String bodyWithoutFooter(Pdf pdf, int page) {
        StringBuilder text = new StringBuilder();
        pdf.glyphs().get(page).stream().filter(glyph -> glyph.baseline() >= QuotePdfRenderer.CONTENT_BOTTOM).forEach(glyph -> text.append(glyph.text()));
        return text.toString();
    }

    // ------------------------------------------------------------------ tests

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 5, 20, 50})
    void documentsOfAnyLengthKeepMarginsFooterAndPagination(int count) throws IOException {
        Quote quote = standard(count);
        Pdf pdf = inspect(RENDERER.render(quote, BUSINESS));
        assertLayout(pdf);
        if (count <= 5) assertThat(pdf.pages()).hasSize(1);
        if (count == 50) assertThat(pdf.pages().size()).isBetween(3, 6);
        for (int number = 1; number <= count; number++) assertThat(pdf.text()).contains("Position %02d".formatted(number));
        String last = pdf.pages().getLast();
        assertThat(last).contains("Total", "CHF " + QuotePdfRenderer.format(quote.getGrandTotal(), 2, 2));
        assertThat(pdf.text().split("Total", -1)).as("total printed once").hasSize(2);
        assertThat(pdf.text()).contains("Vielen Dank für Ihr Vertrauen. Wir freuen uns auf die Zusammenarbeit.")
                .doesNotContain("INTERNE NOTIZ");
        if (count == 0) assertThat(pdf.text()).contains("Keine Positionen erfasst.");
        // Table header repeats on every page that continues the positions.
        for (int page = 1; page < pdf.pages().size(); page++) {
            if (pdf.pages().get(page).contains("Position")) assertThat(pdf.pages().get(page).replaceAll("\\s+", "")).contains("BESCHREIBUNG");
        }
        pdf.document().close();
    }

    @Test void carryOverEqualsTheExactSumOfPrintedAmounts() throws IOException {
        Pdf pdf = inspect(RENDERER.render(standard(50), BUSINESS));
        BigDecimal printed = BigDecimal.ZERO;
        for (int page = 0; page < pdf.pages().size() - 1; page++) {
            String text = pdf.pages().get(page);
            Matcher positions = Pattern.compile("Position \\d{2}").matcher(text);
            while (positions.find()) printed = printed.add(new BigDecimal("10.00"));
            String expected = QuotePdfRenderer.format(printed, 2, 2);
            String next = pdf.pages().get(page + 1);
            if (next.contains("Übertrag von Seite")) {
                assertThat(text).contains("Übertrag").contains(expected);
                assertThat(next).contains("Übertrag von Seite " + (page + 1)).contains(expected);
            }
        }
        assertThat(pdf.pages().get(1)).contains("Übertrag von Seite 1");
        assertThat(pdf.text()).contains("Zwischensumme", "CHF 500.00", "MWST 8.1 % auf 500.00", "CHF 40.50", "CHF 540.50");
        pdf.document().close();
    }

    @Test void longTextsWrapCompletelyAndVeryLongPositionsSplitAcrossPages() throws IOException {
        String sender = "Atelier für Kommunikation, Gestaltung und digitale Produktentwicklung Margraf & Partner GmbH";
        String company = "Genossenschaft für nachhaltige Stadtentwicklung und gemeinschaftliches Wohnen Zürich-Nord";
        String street = "Hinterer Rebbergweg 117b, Gebäude C, 3. Obergeschoss, Büro 3.14, zuhanden Buchhaltung und Projektleitung";
        String essay = ("Ausführliche Leistungsbeschreibung mit vielen Details, Annahmen und Abgrenzungen für das Projekt. ").repeat(60);
        String unbroken = "Leistungsbeschreibung" + "x".repeat(260);
        Quote quote = quote(sender, customer("Genossenschaft Zürich-Nord", company, street, "Zürich"), List.of(
                new ItemSpec("Kurz", "1", ServiceUnit.FIXED, "100.00", "8.10"),
                new ItemSpec("Sehr lange Position\n" + essay, "12.5", ServiceUnit.HOUR, "145.00", "8.10"),
                new ItemSpec(unbroken, "999999999.999", ServiceUnit.MONTH, "1.00", "0"),
                new ItemSpec("Schluss", "3", ServiceUnit.DAY, "1200.00", "2.60")), null);
        Pdf pdf = inspect(RENDERER.render(quote, BUSINESS));
        assertLayout(pdf);
        String compact = pdf.compact();
        for (String text : List.of(sender, company, street, unbroken)) assertThat(compact).contains(text.replaceAll("\\s+", ""));
        // Page breaks fall between lines, never inside words: every word of the 60 sentences is printed.
        for (String word : List.of("Ausführliche", "Leistungsbeschreibung", "Abgrenzungen", "Projekt.")) {
            assertThat(compact.split(Pattern.quote(word), -1).length - 1).as(word).isGreaterThanOrEqualTo(60);
        }
        assertThat(pdf.pages().size()).isGreaterThan(1);
        assertThat(pdf.text()).contains("999’999’999.999 Mt.", "1’000’000’000.00", "MWST 2.6 % auf 3’600.00", "MWST 8.1 % auf");
        assertThat(pdf.text()).contains("CHF " + QuotePdfRenderer.format(quote.getGrandTotal(), 2, 2));
        pdf.document().close();
    }

    @Test void mixedAndZeroTaxRatesShowExactStoredAmounts() throws IOException {
        Quote mixed = quote("Studio Nord", customer("Muster AG", null, null, null), List.of(
                new ItemSpec("Beratung", "10", ServiceUnit.HOUR, "95.10", "8.10"),
                new ItemSpec("Unterkunft", "2", ServiceUnit.DAY, "180.00", "3.80"),
                new ItemSpec("Fachbuch", "1", ServiceUnit.PIECE, "49.90", "2.60"),
                new ItemSpec("Gebühr", "1", ServiceUnit.FIXED, "25.00", "0")), null);
        String text = inspect(RENDERER.render(mixed, BUSINESS)).text();
        assertThat(text).contains("MWST 2.6 % auf 49.90", "CHF 1.30", "MWST 3.8 % auf 360.00", "CHF 13.68",
                "MWST 8.1 % auf 951.00", "CHF 77.03", "CHF 1’385.90", "CHF 1’477.91");
        assertThat(mixed.getGrandTotal()).isEqualByComparingTo("1477.91");

        Quote untaxed = quote("Studio Nord", customer("Muster AG", null, null, null), List.of(new ItemSpec("Beratung", "2", ServiceUnit.HOUR, "100.00", "0")), null);
        Pdf pdf = inspect(RENDERER.render(untaxed, BUSINESS));
        assertLayout(pdf);
        assertThat(pdf.text()).contains("MWST", "CHF 0.00", "CHF 200.00").doesNotContainPattern("MWST \\d");
        pdf.document().close();
    }

    @Test void europeanCharactersAreEmbeddedAndUnsupportedScriptsAreVisiblyReplaced() throws IOException {
        String name = "Zoë Łukasiewicz-Ærøskøbing";
        String company = "Ștefan Țepeș & François Garçon Sàrl";
        String street = "Ősz utca 5 · Αθήνα · Москва";
        Quote quote = quote("Café Müller – Lüdenscheid & Söhne", customer(name, company, street, "Genève"), List.of(
                new ItemSpec("Übersetzung „Grüße“ ‹Qualität› € 5 – Ž Č Š ő ű ł ñ ß", "1", ServiceUnit.FIXED, "10.00", "0"),
                new ItemSpec("Kombinierte Zeichen: é ä · 漢字 🚀", "1", ServiceUnit.FIXED, "10.00", "0")), null);
        Pdf pdf = inspect(RENDERER.render(quote, BUSINESS));
        assertLayout(pdf);
        assertThat(pdf.text()).contains(name, company, street, "Genève", "Café Müller – Lüdenscheid & Söhne");
        assertThat(pdf.compact()).contains("Übersetzung„Grüße“‹Qualität›€5–ŽČŠőűłñß", "KombinierteZeichen:éä·???");
        for (PDPage page : pdf.document().getPages()) {
            for (COSName fontName : page.getResources().getFontNames()) {
                PDFont font = page.getResources().getFont(fontName);
                assertThat(font.isEmbedded()).as(font.getName()).isTrue();
                assertThat(font.getName()).contains("Inter");
            }
        }
        pdf.document().close();
    }

    @Test void onlySnapshotDataIsPrintedAndDraftsAreMarked() throws IOException {
        Quote quote = standard(2);
        Pdf draft = inspect(RENDERER.render(quote, BUSINESS));
        assertThat(draft.text()).contains("Studio Nord", "Muster AG", "Erika Muster", "Bahnhofstrasse 1", "8001 Zürich",
                "Kundennummer K-0007", "ANG-0042", "18.11.2026", "18.12.2026", "Gültig bis 18.12.2026 · Beträge in CHF",
                "Angebot für Muster AG", "Vielen Dank für Ihre Anfrage.")
                .doesNotContain("Live-Name", "INTERNE NOTIZ");
        assertThat(draft.compact()).contains("ANGEBOT", "ENTWURF", "EMPFÄNGER", "ANGEBOTSDETAILS");
        assertThat(draft.document().getDocumentInformation().getTitle()).isEqualTo("Angebot ANG-0042");
        assertThat(draft.document().getDocumentCatalog().getLanguage()).isEqualTo("de-CH");
        draft.document().close();
        quote.transitionTo(QuoteStatus.SENT, Instant.now());
        Pdf sent = inspect(RENDERER.render(quote, BUSINESS));
        assertThat(sent.compact()).doesNotContain("ENTWURF");
        assertThat(sent.text()).doesNotContain("Schweiz"); // domestic country omitted
        sent.document().close();
    }

    @Test void brandingHooksDrawLogoContactDetailsAndThemeWithoutBreakingTheLayout() throws IOException {
        BufferedImage logo = new BufferedImage(300, 90, BufferedImage.TYPE_INT_RGB);
        var graphics = logo.createGraphics();
        graphics.setColor(new Color(0x245747)); graphics.fillRect(0, 0, 300, 90); graphics.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(logo, "png", png);
        DocumentSender sender = new DocumentSender("Studio Nord GmbH", List.of("Seestrasse 12", "8002 Zürich"),
                List.of("hallo@studio-nord.ch", "+41 44 000 00 00"), png.toByteArray());
        DocumentTheme theme = DocumentTheme.hustivoo().withColors(new Color(0x1F3A5F), new Color(0xE8EEF5)).withFooterNote("IBAN CH00 0000 0000 0000 0000 0");
        Pdf pdf = inspect(RENDERER.render(standard(30), sender, theme, "CH"));
        assertLayout(pdf);
        assertThat(pdf.pages().getFirst()).contains("Seestrasse 12", "8002 Zürich", "hallo@studio-nord.ch");
        for (String page : pdf.pages()) assertThat(page).contains("IBAN CH00 0000 0000 0000 0000 0");  // payment details are never cut off
        PDPage first = pdf.document().getPage(0);
        boolean image = false;
        for (COSName name : first.getResources().getXObjectNames()) image |= first.getResources().getXObject(name) instanceof PDImageXObject;
        assertThat(image).isTrue();
        pdf.document().close();
        // A broken logo is skipped instead of failing the download.
        Pdf broken = inspect(RENDERER.render(standard(1), new DocumentSender("Studio Nord", List.of(), List.of(), new byte[] {1, 2, 3}), DocumentTheme.hustivoo(), "CH"));
        assertThat(broken.text()).contains("Studio Nord");
        broken.document().close();
    }

    @Test void footerTruncatesOnlyRepeatedTextAndNeverCollides() throws IOException {
        String sender = "Ein ausserordentlich langer Unternehmensname, der in der Fusszeile gekürzt werden muss, damit nichts kollidiert AG";
        Quote quote = quote(sender, customer("Muster AG", null, null, null), List.of(item(1)), null);
        Pdf pdf = inspect(RENDERER.render(quote, BUSINESS));
        assertLayout(pdf);
        assertThat(pdf.compact()).contains(sender.replaceAll("\\s+", ""));  // full name in the letterhead
        String footer = pdf.glyphs().getFirst().stream().filter(glyph -> glyph.baseline() < QuotePdfRenderer.FOOTER_RULE)
                .map(Glyph::text).reduce("", String::concat);
        assertThat(footer).contains("…").contains("Seite1von1");
        assertThat(bodyWithoutFooter(pdf, 0)).contains("Total");
        pdf.document().close();
    }
}
