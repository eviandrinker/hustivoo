package digital.margraf.hustivoo_backend.invoice;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import digital.margraf.hustivoo_backend.business.SenderProfile;
import digital.margraf.hustivoo_backend.customer.Customer;
import digital.margraf.hustivoo_backend.customer.CustomerRequest;
import digital.margraf.hustivoo_backend.customer.CustomerType;
import digital.margraf.hustivoo_backend.document.BusinessDocumentRenderer;
import digital.margraf.hustivoo_backend.document.qrbill.QrBillSlip;
import digital.margraf.hustivoo_backend.document.qrbill.SwissQrBill;
import digital.margraf.hustivoo_backend.quote.QuoteCalculator;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/** Invoice PDFs: shared layout, QR payment part geometry and the decoded Swiss QR Code. */
class InvoicePdfLayoutTests {
    static final InvoicePdfRenderer RENDERER = new InvoicePdfRenderer();
    static final float MM = QrBillSlip.MM;
    static final SenderProfile SENDER = new SenderProfile("Atelier Bergwerk GmbH", "CH", "Seestrasse 12", "8002", "Zürich",
            "hallo@atelier-bergwerk.ch", "+41 44 123 45 67", "www.atelier-bergwerk.ch", "CHE-116.281.710 MWST",
            "CH9300762011623852957", "Zürcher Kantonalbank", "Vielen Dank für Ihren Auftrag.", "#7A2E3B", null);

    // ------------------------------------------------------------------ fixtures

    static Customer customer(String street, String postalCode, String city) {
        return new Customer(UUID.randomUUID(), new CustomerRequest(CustomerType.COMPANY, "Muster AG", "Muster AG", "Erika", "Muster",
                null, null, street, postalCode, city, "CH", null), "K-0007");
    }

    static Invoice invoice(int count, Customer customer, boolean issue) {
        Invoice invoice = new Invoice(UUID.randomUUID(), "RE-0042", null);
        invoice.editHeader(LocalDate.of(2026, 11, 18), 30, "CHF", "Vielen Dank für Ihren Auftrag. Wir verrechnen folgende Leistungen.",
                "Freundliche Grüsse", "INTERN – nie im PDF");
        invoice.snapshot(SENDER.name(), SENDER.country(), customer);
        List<InvoiceItem> items = new ArrayList<>();
        List<QuoteCalculator.Line> lines = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            var line = QuoteCalculator.line(new BigDecimal("2"), new BigDecimal("120.00"), new BigDecimal("8.10"), "CHF");
            lines.add(line);
            items.add(new InvoiceItem(invoice, number, null, "Position %02d Umsetzung\nKonzept, Gestaltung und Abstimmung".formatted(number),
                    new BigDecimal("2"), ServiceUnit.HOUR, new BigDecimal("120.00"), new BigDecimal("8.10"), line));
        }
        invoice.replaceItems(items, QuoteCalculator.totals(lines));
        if (issue) invoice.issue(SENDER, Instant.now());
        return invoice;
    }

    record Glyph(String text, float x, float baseline, String font) {}
    record Pdf(PDDocument document, String text, List<List<Glyph>> glyphs) {}

    static Pdf inspect(byte[] bytes) throws IOException {
        String directory = System.getProperty("quote.pdf.out");
        if (directory != null) java.nio.file.Files.write(java.nio.file.Path.of(directory, "invoice-" + System.nanoTime() + ".pdf"), bytes);
        PDDocument document = Loader.loadPDF(bytes);
        List<List<Glyph>> pages = new ArrayList<>();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            List<Glyph> glyphs = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override protected void writeString(String text, List<TextPosition> positions) {
                    for (TextPosition position : positions) {
                        glyphs.add(new Glyph(position.getUnicode(), position.getXDirAdj(),
                                position.getPageHeight() - position.getYDirAdj(), position.getFont().getName()));
                    }
                }
            };
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            stripper.getText(document);
            pages.add(glyphs);
        }
        return new Pdf(document, new PDFTextStripper().getText(document), pages);
    }

    static String decodeQr(PDDocument document, int page) throws Exception {
        BufferedImage image = new PDFRenderer(document).renderImageWithDPI(page, 300);
        // Crop the payment part so the decoder sees only the Swiss QR Code region.
        int scale = 300;
        int x = Math.round((62 + 5 - 3) / 25.4f * scale);
        int size = Math.round((46 + 6) / 25.4f * scale);
        int y = image.getHeight() - Math.round((105 - 17 + 3) / 25.4f * scale);
        BufferedImage region = image.getSubimage(x, y, size, size);
        int[] pixels = region.getRGB(0, 0, size, size, null, 0, size);
        var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(size, size, pixels)));
        return new QRCodeReader().decode(bitmap, Map.of(DecodeHintType.TRY_HARDER, true, DecodeHintType.CHARACTER_SET, "UTF-8")).getText();
    }

    static Optional<Glyph> first(List<Glyph> glyphs, String word) {
        StringBuilder text = new StringBuilder();
        for (Glyph glyph : glyphs) text.append(glyph.text());
        int index = text.indexOf(word);
        return index < 0 ? Optional.empty() : Optional.of(glyphs.get(index));
    }

    // ------------------------------------------------------------------ tests

    @ParameterizedTest @ValueSource(ints = {1, 5, 20, 50})
    void issuedInvoiceCarriesAValidPaymentPartOnItsLastPage(int count) throws Exception {
        Invoice invoice = invoice(count, customer("Bahnhofstrasse 1", "3000", "Bern"), true);
        SwissQrBill.Result expected = InvoicePdfRenderer.qrBill(invoice, SENDER);
        assertThat(expected.problems()).isEmpty();
        Pdf pdf = inspect(RENDERER.render(invoice, SENDER, null, null));
        int last = pdf.glyphs().size() - 1;
        assertThat(decodeQr(pdf.document(), last)).isEqualTo(expected.bill().payload());
        assertThat(expected.bill().payload()).contains("\n" + invoice.getGrandTotal().toPlainString() + "\nCHF\n", "\nRechnung RE-0042\nEPD");

        List<Glyph> slipPage = pdf.glyphs().get(last);
        Glyph receipt = first(slipPage, "Empfangsschein").orElseThrow();
        Glyph payment = first(slipPage, "Zahlteil").orElseThrow();
        assertThat(receipt.x()).isCloseTo(5 * MM, within(0.5f));
        assertThat(payment.x()).isCloseTo(67 * MM, within(0.5f));
        assertThat(payment.baseline()).isCloseTo(receipt.baseline(), within(0.1f));
        assertThat(receipt.baseline()).isBetween(95 * MM, 100 * MM);
        assertThat(first(slipPage, "Konto / Zahlbar an").orElseThrow().x()).isCloseTo(5 * MM, within(0.5f));
        assertThat(first(slipPage.subList(slipPage.indexOf(payment), slipPage.size()), "Konto / Zahlbar an").orElseThrow().x())
                .isCloseTo(118 * MM, within(0.5f));
        assertThat(pdf.text()).contains("CH93 0076 2011 6238 5295 7", "Annahmestelle", "Vor der Einzahlung abzutrennen",
                "Zahlbar durch", "Muster AG", "Bahnhofstrasse 1", "Zusätzliche Informationen", "Rechnung RE-0042");
        assertThat(pdf.text()).doesNotContain("INTERN");

        // Only permitted fonts in the payment part; invoice content never reaches into it.
        for (Glyph glyph : slipPage) {
            if (glyph.baseline() < QrBillSlip.SLIP_HEIGHT + 4 * MM) {
                assertThat(glyph.font()).as("slip glyph %s", glyph).contains("LiberationSans");
            } else {
                assertThat(glyph.font()).as("document glyph %s", glyph).contains("Inter");
            }
        }
        for (int page = 0; page < last; page++) {
            for (Glyph glyph : pdf.glyphs().get(page)) assertThat(glyph.baseline()).isGreaterThan(20);
            assertThat(first(pdf.glyphs().get(page), "Zahlteil")).isEmpty();
        }
        assertThat(pdf.text()).contains("Seite " + (last + 1) + " von " + (last + 1));
        if (count == 1) assertThat(pdf.glyphs()).hasSize(1);
        pdf.document().close();
    }

    @Test void slipFootersSitAboveThePaymentPart() throws Exception {
        Pdf pdf = inspect(RENDERER.render(invoice(1, customer("Bahnhofstrasse 1", "3000", "Bern"), true), SENDER, null, null));
        List<Glyph> page = pdf.glyphs().getFirst();
        Glyph footer = first(page, "Seite 1 von 1").orElseThrow();
        assertThat(footer.baseline()).isGreaterThan(QrBillSlip.SLIP_HEIGHT + 4 * MM);
        // The lowest invoice content glyph stays clearly above the compact footer line.
        float lowestContent = (float) page.stream().filter(glyph -> glyph.font().contains("Inter") && glyph.baseline() > footer.baseline() + 1)
                .mapToDouble(Glyph::baseline).min().orElseThrow();
        assertThat(lowestContent).isGreaterThan(footer.baseline() + 20);
        pdf.document().close();
    }

    @Test void draftsShowTheNotForPaymentVariant() throws Exception {
        Invoice draft = invoice(2, customer("Bahnhofstrasse 1", "3000", "Bern"), false);
        Pdf pdf = inspect(RENDERER.render(draft, SENDER, null, null));
        String payload = decodeQr(pdf.document(), pdf.glyphs().size() - 1);
        assertThat(payload).contains("\n0.00\nCHF\n").endsWith("NICHT ZUR ZAHLUNG VERWENDEN\nEPD");
        assertThat(pdf.text()).contains("NICHT ZUR ZAHLUNG VERWENDEN", "0.00");
        assertThat(pdf.text().replaceAll("\\s+", "")).contains("ENTWURF");
        pdf.document().close();
    }

    @Test void debtorWithoutAddressGetsTheBlankFields() throws Exception {
        Invoice invoice = invoice(1, customer(null, null, null), true);
        assertThat(InvoicePdfRenderer.qrBill(invoice, SENDER).bill().debtor()).isNull();
        Pdf pdf = inspect(RENDERER.render(invoice, SENDER, null, null));
        assertThat(pdf.text()).contains("Zahlbar durch (Name/Adresse)");
        assertThat(decodeQr(pdf.document(), 0)).contains("\nCHF\n\n\n\n\n\n\n\nNON\n");
        pdf.document().close();
    }

    @Test void missingPaymentDataProducesNoQrCodeButAReason() throws Exception {
        SenderProfile noIban = new SenderProfile("Atelier Bergwerk GmbH", "CH", "Seestrasse 12", "8002", "Zürich",
                null, null, null, null, null, null, null, null, null);
        Invoice invoice = invoice(1, customer("Bahnhofstrasse 1", "3000", "Bern"), true);
        SwissQrBill.Result result = InvoicePdfRenderer.qrBill(invoice, noIban);
        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).anyMatch(problem -> problem.contains("keine IBAN"));
        Pdf pdf = inspect(RENDERER.render(invoice, noIban, null, null));
        assertThat(pdf.text()).doesNotContain("Zahlteil", "Empfangsschein").contains("Total", "Zahlbar bis 18.12.2026");
        pdf.document().close();

        SenderProfile noAddress = new SenderProfile("Atelier", "CH", null, null, null, null, null, null, null,
                "CH9300762011623852957", null, null, null, null);
        assertThat(InvoicePdfRenderer.qrBill(invoice, noAddress).problems()).anyMatch(problem -> problem.contains("Postleitzahl und Ort"));
    }

    @Test void cancelledInvoicesCarryNoPaymentPart() throws Exception {
        Invoice invoice = invoice(1, customer("Bahnhofstrasse 1", "3000", "Bern"), true);
        invoice.cancel("Doppelt erfasst", Instant.now());
        Pdf pdf = inspect(RENDERER.render(invoice, SENDER, null, null));
        assertThat(pdf.text()).doesNotContain("Zahlteil");
        assertThat(pdf.text().replaceAll("\\s+", "")).contains("STORNIERT");
        pdf.document().close();
    }

    @Test void invoiceDocumentShowsDueDateTermAndQuoteReference() throws Exception {
        Invoice invoice = invoice(3, customer("Bahnhofstrasse 1", "3000", "Bern"), true);
        Pdf pdf = inspect(RENDERER.render(invoice, SENDER, null, "ANG-0007"));
        assertThat(pdf.text()).contains("Rechnung für Muster AG", "Rechnungsnummer", "RE-0042", "Rechnungsdatum", "18.11.2026", "Fällig am", "18.12.2026", "30 Tage", "ANG-0007",
                "Zahlbar bis 18.12.2026 · Beträge in CHF", "Vielen Dank für Ihren Auftrag.");
        assertThat(pdf.text().replaceAll("\\s+", "")).contains("RECHNUNG");
        // Amounts in the PDF are exactly the stored ones.
        assertThat(pdf.text()).contains("CHF " + BusinessDocumentRenderer.DocumentFormat.number(invoice.getGrandTotal(), 2, 2));
        pdf.document().close();
    }
}
