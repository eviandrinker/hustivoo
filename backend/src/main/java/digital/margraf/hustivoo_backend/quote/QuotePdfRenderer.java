package digital.margraf.hustivoo_backend.quote;

import digital.margraf.hustivoo_backend.business.BusinessResponse;
import digital.margraf.hustivoo_backend.document.DocumentFonts;
import digital.margraf.hustivoo_backend.document.DocumentFonts.Weight;
import digital.margraf.hustivoo_backend.document.DocumentSender;
import digital.margraf.hustivoo_backend.document.DocumentTheme;
import digital.margraf.hustivoo_backend.document.PdfCanvas;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A4 quote document. Uses only data stored on the quote (item, customer and sender snapshots and the
 * server-calculated amounts), so issued quotes keep printing exactly what was sent. Amounts are
 * formatted, never recalculated; the carry-over ("Übertrag") is the exact sum of printed line amounts.
 *
 * <p>Layout rules: pages are created lazily (no empty pages); the table header is never left alone at
 * the bottom of a page and repeats on continuation pages; rows are kept together unless a single row is
 * taller than a page; the totals block is kept together; nothing is drawn below the footer zone.
 */
@Component
public class QuotePdfRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(QuotePdfRenderer.class);

    // Geometry (points). Content never goes below CONTENT_BOTTOM; the footer lives beneath FOOTER_RULE.
    static final float LEFT = 62;
    static final float RIGHT = PdfCanvas.WIDTH - 62;
    static final float CONTENT_WIDTH = RIGHT - LEFT;
    static final float TOP = PdfCanvas.HEIGHT - 60;
    static final float FOOTER_RULE = 62;
    static final float CONTENT_BOTTOM = FOOTER_RULE + 28;
    private static final float CONTINUATION_TOP = TOP - 44;
    private static final float TEXT_WIDTH = 410;              // comfortable line length for prose
    private static final float CARRY_HEIGHT = 26;             // reserved below rows for "Übertrag"
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public byte[] render(Quote quote, BusinessResponse business) {
        return render(quote, DocumentSender.named(quote.getSenderName()), DocumentTheme.hustivoo(), business.country());
    }

    /** Entry point for future branding: logo, address/contact lines, colours and footer note. */
    public byte[] render(Quote quote, DocumentSender sender, DocumentTheme theme, String homeCountry) {
        try (PDDocument document = new PDDocument()) {
            DocumentFonts fonts = DocumentFonts.load(document);
            try (PdfCanvas canvas = new PdfCanvas(document, fonts)) {
                new Layout(canvas, quote, sender, theme, homeCountry).write();
            }
            if (!fonts.missing().isEmpty()) {
                LOG.warn("Quote {}: {} character(s) not covered by the document font were printed as '?': {}", quote.getId(),
                        fonts.missing().size(), fonts.missing().stream().map(cp -> String.format("U+%04X", cp)).collect(Collectors.joining(" ")));
            }
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle("Angebot " + quote.getQuoteNumber());
            info.setSubject("Angebot " + quote.getQuoteNumber() + " für " + quote.getCustomerName());
            info.setAuthor(sender.name());
            info.setCreator("Hustivoo");
            info.setCreationDate(Calendar.getInstance());
            document.getDocumentCatalog().setLanguage("de-CH");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class Layout {
        private final PdfCanvas canvas;
        private final Quote quote;
        private final DocumentSender sender;
        private final DocumentTheme theme;
        private final String homeCountry;
        private final int digits;
        private float y;
        private BigDecimal carried = BigDecimal.ZERO.setScale(2);
        private int rowsOnPage;
        // Table geometry, sized so that no amount is ever clipped.
        private float descX, descWidth, qtyRight, priceRight, taxRight, amountWidth;

        Layout(PdfCanvas canvas, Quote quote, DocumentSender sender, DocumentTheme theme, String homeCountry) {
            this.canvas = canvas; this.quote = quote; this.sender = sender; this.theme = theme; this.homeCountry = homeCountry;
            this.digits = QuoteCalculator.minorDigits(quote.getCurrency());
        }

        void write() throws IOException {
            canvas.newPage();
            letterhead();
            parties();
            subject();
            columns();
            table();
            totals();
            closing();
            footers();
        }

        // ------------------------------------------------------------------ first page

        private void letterhead() throws IOException {
            canvas.fill(theme.primary(), LEFT, TOP + 22, 26, 2.4f);
            float leftWidth = CONTENT_WIDTH - 200;
            float base = TOP - 11;
            if (sender.logo() != null) {
                try {
                    float height = canvas.image(sender.logo(), LEFT, TOP + 8, 150, 42);
                    base = TOP + 8 - height - 22;
                } catch (IOException | IllegalArgumentException exception) {
                    LOG.warn("Quote {}: sender logo could not be drawn and was omitted", quote.getId());
                }
            }
            for (String line : canvas.wrap(sender.name(), Weight.SEMIBOLD, 15, leftWidth)) {
                canvas.text(line, Weight.SEMIBOLD, 15, theme.text(), LEFT, base);
                base -= 19;
            }
            base += 3;
            for (String entry : concat(sender.addressLines(), sender.contactLines())) {
                for (String line : canvas.wrap(entry, Weight.REGULAR, 8.5f, leftWidth)) {
                    canvas.text(line, Weight.REGULAR, 8.5f, theme.muted(), LEFT, base);
                    base -= 12.5f;
                }
            }
            float right = TOP - 11;
            canvas.textRight("ANGEBOT", Weight.BOLD, 12, theme.primary(), RIGHT, right, 1.8f);
            right -= 17;
            canvas.textRight("Nr. " + quote.getQuoteNumber(), Weight.REGULAR, 9, theme.muted(), RIGHT, right);
            if (quote.getStatus() == QuoteStatus.DRAFT) {
                float width = canvas.width("ENTWURF", Weight.SEMIBOLD, 6.5f, 0.9f) + 14;
                right -= 23;
                canvas.roundedRect(RIGHT - width, right - 4.5f, width, 14, 7, theme.secondary(), null, 0);
                canvas.text("ENTWURF", Weight.SEMIBOLD, 6.5f, theme.warning(), RIGHT - width + 7, right, 0.9f);
            }
            y = Math.min(Math.min(base, right) - 40, TOP - 108);
        }

        private void parties() throws IOException {
            float top = y;
            float columnWidth = CONTENT_WIDTH / 2 - 16;
            label("EMPFÄNGER", LEFT, top);
            float left = top - 18;
            String name = quote.getCustomerName();
            for (String line : canvas.wrap(name, Weight.SEMIBOLD, 10.5f, columnWidth)) {
                canvas.text(line, Weight.SEMIBOLD, 10.5f, theme.text(), LEFT, left);
                left -= 14.5f;
            }
            List<String> lines = new ArrayList<>();
            addIfDistinct(lines, quote.getCustomerCompanyName(), name);
            addIfDistinct(lines, quote.getCustomerContactName(), name);
            if (quote.getCustomerStreet() != null) lines.add(quote.getCustomerStreet());
            String place = join(" ", quote.getCustomerPostalCode(), quote.getCustomerCity());
            if (!place.isEmpty()) lines.add(place);
            if (!quote.getCustomerCountry().equals(homeCountry)) lines.add(Locale.of("", quote.getCustomerCountry()).getDisplayCountry(Locale.GERMAN));
            for (String entry : lines) {
                for (String line : canvas.wrap(entry, Weight.REGULAR, 9.5f, columnWidth)) {
                    canvas.text(line, Weight.REGULAR, 9.5f, theme.text(), LEFT, left);
                    left -= 14;
                }
            }
            left -= 6;
            canvas.text("Kundennummer " + quote.getCustomerNumber(), Weight.REGULAR, 8.5f, theme.muted(), LEFT, left);

            float metaX = RIGHT - 196;
            label("ANGEBOTSDETAILS", metaX, top);
            float right = top - 18;
            String[][] rows = {
                    {"Angebotsnummer", quote.getQuoteNumber()},
                    {"Datum", DATE.format(quote.getIssueDate())},
                    {"Gültig bis", DATE.format(quote.getValidUntil())},
                    {"Währung", quote.getCurrency()}};
            for (String[] row : rows) {
                canvas.text(row[0], Weight.REGULAR, 8.5f, theme.muted(), metaX, right);
                canvas.textRight(row[1], row == rows[0] ? Weight.SEMIBOLD : Weight.REGULAR, 9.5f, theme.text(), RIGHT, right);
                right -= 15.5f;
            }
            y = Math.min(left, right) - 36;
        }

        private void subject() throws IOException {
            for (String line : canvas.wrap("Angebot für " + quote.getCustomerName(), Weight.SEMIBOLD, 17, CONTENT_WIDTH)) {
                ensure(22);
                canvas.text(line, Weight.SEMIBOLD, 17, theme.text(), LEFT, y);
                y -= 22;
            }
            if (quote.getIntroductionText() != null) {
                y -= 6;
                prose(quote.getIntroductionText());
            }
            y -= 22;
        }

        // ------------------------------------------------------------------ positions

        private void columns() {
            float qtyWidth = Math.max(58, widest(Weight.REGULAR, 9.5f, quote.getItems().stream().map(item -> quantity(item.getQuantity()) + " " + unit(item.getUnit()))) + 14);
            float priceWidth = Math.max(68, widest(Weight.REGULAR, 9.5f, quote.getItems().stream().map(item -> money(item.getUnitPrice()))) + 14);
            float taxWidth = Math.max(42, widest(Weight.REGULAR, 9.5f, quote.getItems().stream().map(item -> rate(item.getTaxRate()))) + 12);
            amountWidth = Math.max(74, widest(Weight.SEMIBOLD, 9.5f, Stream.concat(quote.getItems().stream().map(item -> money(item.getLineSubtotal())),
                    Stream.of(money(quote.getSubtotal())))) + 14);
            taxRight = RIGHT - amountWidth;
            priceRight = taxRight - taxWidth;
            qtyRight = priceRight - priceWidth;
            descX = LEFT + 24;
            descWidth = qtyRight - qtyWidth - descX;
        }

        private float widest(Weight weight, float size, Stream<String> values) {
            return (float) values.mapToDouble(value -> canvas.width(value, weight, size)).max().orElse(0);
        }

        private record Line(String text, boolean title, float offset) {}

        private List<Line> lines(QuoteItem item) {
            String[] parts = item.getDescription().split("\\R", 2);
            List<Line> lines = new ArrayList<>();
            for (String text : canvas.wrap(parts[0], Weight.SEMIBOLD, 9.5f, descWidth)) lines.add(new Line(text, true, lines.isEmpty() ? 15 : 13.5f));
            if (parts.length > 1) {
                boolean first = true;
                for (String text : canvas.wrap(parts[1], Weight.REGULAR, 8.5f, descWidth)) {
                    lines.add(new Line(text, false, first ? 14 : 12));
                    first = false;
                }
            }
            return lines;
        }

        private static float height(List<Line> lines) {
            return (float) lines.stream().mapToDouble(Line::offset).sum() + 10;
        }

        private float freshCapacity() { return CONTINUATION_TOP - 26 - 32 - CONTENT_BOTTOM - CARRY_HEIGHT; }

        private void table() throws IOException {
            List<QuoteItem> items = quote.getItems();
            if (items.isEmpty()) {
                ensure(60);
                tableHeader();
                y -= 18;
                canvas.text("Keine Positionen erfasst.", Weight.REGULAR, 9.5f, theme.muted(), descX, y);
                y -= 12;
                canvas.rule(theme.rule(), LEFT, RIGHT, y, 0.5f);
                return;
            }
            // Never leave the header alone: it needs room for the start of the first position.
            float firstMinimum = Math.min(height(lines(items.getFirst())), 15 + 2 * 13.5f + 10);
            if (y - 26 - firstMinimum - CARRY_HEIGHT < CONTENT_BOTTOM) continuationPage();
            tableHeader();
            for (QuoteItem item : items) {
                List<Line> lines = lines(item);
                float height = height(lines);
                if (y - height - CARRY_HEIGHT < CONTENT_BOTTOM && rowsOnPage > 0 && height <= freshCapacity()) breakTable();
                row(item, lines);
            }
        }

        private void tableHeader() throws IOException {
            float base = y - 8;
            label("POS.", LEFT, base);
            label("BESCHREIBUNG", descX, base);
            labelRight("MENGE", qtyRight, base);
            labelRight("EINZELPREIS", priceRight, base);
            labelRight("MWST", taxRight, base);
            labelRight("BETRAG " + quote.getCurrency(), RIGHT, base);
            y = base - 9;
            canvas.rule(theme.text(), LEFT, RIGHT, y, 0.6f);
            rowsOnPage = 0;
        }

        /** Draws one position; only a position taller than a whole page is split across pages. */
        private void row(QuoteItem item, List<Line> lines) throws IOException {
            boolean numbers = false;
            for (int index = 0; index < lines.size(); index++) {
                Line line = lines.get(index);
                float offset = line.offset();
                boolean segmentStart = index == 0;
                if (y - offset - 10 - CARRY_HEIGHT < CONTENT_BOTTOM) {
                    breakTable();
                    offset = 15;
                    segmentStart = true;
                }
                y -= offset;
                if (segmentStart) canvas.text(String.valueOf(item.getPosition()), Weight.REGULAR, 9, theme.muted(), LEFT, y);
                if (!numbers) {
                    canvas.textRight(quantity(item.getQuantity()) + " " + unit(item.getUnit()), Weight.REGULAR, 9.5f, theme.text(), qtyRight, y);
                    canvas.textRight(money(item.getUnitPrice()), Weight.REGULAR, 9.5f, theme.text(), priceRight, y);
                    canvas.textRight(rate(item.getTaxRate()), Weight.REGULAR, 9.5f, theme.muted(), taxRight, y);
                    canvas.textRight(money(item.getLineSubtotal()), Weight.SEMIBOLD, 9.5f, theme.text(), RIGHT, y);
                    carried = carried.add(item.getLineSubtotal());
                    numbers = true;
                }
                canvas.text(line.text(), line.title() ? Weight.SEMIBOLD : Weight.REGULAR, line.title() ? 9.5f : 8.5f,
                        line.title() ? theme.text() : theme.muted(), descX, y);
            }
            y -= 10;
            canvas.rule(theme.rule(), LEFT, RIGHT, y, 0.5f);
            rowsOnPage++;
        }

        private void breakTable() throws IOException {
            carryOut();
            continuationPage();
            tableHeader();
            carryIn();
        }

        private void carryOut() throws IOException {
            float base = y - 16;
            canvas.textRight("Übertrag", Weight.REGULAR, 8.5f, theme.muted(), RIGHT - amountWidth, base);
            canvas.textRight(money(carried), Weight.SEMIBOLD, 9, theme.text(), RIGHT, base);
        }

        private void carryIn() throws IOException {
            y -= 16;
            canvas.text("Übertrag von Seite " + (canvas.pageCount() - 1), Weight.REGULAR, 8.5f, theme.muted(), descX, y);
            canvas.textRight(money(carried), Weight.SEMIBOLD, 9, theme.text(), RIGHT, y);
            y -= 10;
            canvas.rule(theme.rule(), LEFT, RIGHT, y, 0.5f);
        }

        // ------------------------------------------------------------------ totals and closing

        private void totals() throws IOException {
            List<QuoteCalculator.TaxGroup> taxed = QuoteCalculator.taxGroups(quote.getItems()).stream().filter(group -> group.rate().signum() > 0).toList();
            int rows = 1 + Math.max(1, taxed.size());
            // Exact height: gap, rows, total rule and line, validity note and its descender.
            float block = 26 + rows * 17 - 3 + 24 + 20 + 4;
            // A short closing text stays with the totals instead of ending up alone on an extra page.
            int closingLines = quote.getClosingText() == null ? 0 : wrapProse(quote.getClosingText()).size();
            if (closingLines > 0 && closingLines <= 6) block += 40 + closingLines * 14.5f;
            if (y - block < CONTENT_BOTTOM) {
                if (!quote.getItems().isEmpty()) carryOut();
                continuationPage();
                if (!quote.getItems().isEmpty()) carryIn();
            }
            float labelX = RIGHT - 270;
            String currency = quote.getCurrency() + " ";
            y -= 26;
            totalRow("Zwischensumme", currency + money(quote.getSubtotal()), labelX);
            if (taxed.isEmpty()) totalRow("MWST", currency + money(quote.getTaxTotal()), labelX);
            for (QuoteCalculator.TaxGroup group : taxed) {
                totalRow("MWST " + rate(group.rate()) + " auf " + money(group.base()), currency + money(group.tax()), labelX);
            }
            y += 3;
            canvas.rule(theme.primary(), labelX, RIGHT, y, 1.2f);
            y -= 24;
            canvas.text("Total", Weight.SEMIBOLD, 11, theme.text(), labelX, y);
            canvas.textRight(currency + money(quote.getGrandTotal()), Weight.BOLD, 15, theme.text(), RIGHT, y);
            y -= 20;
            canvas.textRight("Gültig bis " + DATE.format(quote.getValidUntil()) + " · Beträge in " + quote.getCurrency(),
                    Weight.REGULAR, 8, theme.muted(), RIGHT, y);
        }

        private void totalRow(String label, String value, float labelX) throws IOException {
            canvas.text(label, Weight.REGULAR, 9, theme.muted(), labelX, y);
            canvas.textRight(value, Weight.REGULAR, 9.5f, theme.text(), RIGHT, y);
            y -= 17;
        }

        private void closing() throws IOException {
            if (quote.getClosingText() == null) return;
            y -= 40;
            prose(quote.getClosingText());
        }

        private List<String> wrapProse(String value) {
            return canvas.wrap(value, Weight.REGULAR, 9.5f, Math.min(TEXT_WIDTH, CONTENT_WIDTH));
        }

        private void prose(String value) throws IOException {
            for (String line : wrapProse(value)) {
                ensure(14.5f);
                if (!line.isEmpty()) canvas.text(line, Weight.REGULAR, 9.5f, theme.text(), LEFT, y);
                y -= 14.5f;
            }
        }

        // ------------------------------------------------------------------ pages

        private void ensure(float height) throws IOException {
            if (y - height < CONTENT_BOTTOM) continuationPage();
        }

        private void continuationPage() throws IOException {
            canvas.newPage();
            float base = TOP - 2;
            canvas.text(canvas.fit(sender.name(), Weight.SEMIBOLD, 8.5f, CONTENT_WIDTH - 200), Weight.SEMIBOLD, 8.5f, theme.text(), LEFT, base);
            canvas.textRight("Angebot " + quote.getQuoteNumber(), Weight.REGULAR, 8.5f, theme.muted(), RIGHT, base);
            canvas.rule(theme.rule(), LEFT, RIGHT, base - 11, 0.5f);
            y = CONTINUATION_TOP;
        }

        private void footers() throws IOException {
            int count = canvas.pageCount();
            List<String> entries = concat(concat(sender.addressLines(), sender.contactLines()),
                    theme.footerNote() == null ? List.of() : List.of(theme.footerNote()));
            float leftWidth = CONTENT_WIDTH - 150;
            List<String> lines = footerLines(entries, leftWidth);
            boolean details = !lines.isEmpty();
            for (int index = 0; index < count; index++) {
                int page = index + 1;
                canvas.onPage(index, () -> {
                    canvas.rule(theme.rule(), LEFT, RIGHT, FOOTER_RULE, 0.5f);
                    canvas.text(canvas.fit(sender.name(), Weight.SEMIBOLD, 7.5f, leftWidth), Weight.SEMIBOLD, 7.5f, theme.text(), LEFT, details ? 46 : 42);
                    for (int line = 0; line < lines.size(); line++) {
                        canvas.text(lines.get(line), Weight.REGULAR, 7.5f, theme.muted(), LEFT, 35 - line * 10);
                    }
                    canvas.textRight("Angebot " + quote.getQuoteNumber(), Weight.REGULAR, 7.5f, theme.muted(), RIGHT, 46);
                    canvas.textRight("Seite " + page + " von " + count, Weight.REGULAR, 7.5f, theme.muted(), RIGHT, 35);
                });
            }
        }

        /**
         * Footer details on at most two lines. Entries (address, contact, payment note) are never split
         * between lines; only an entry longer than a whole line, or a third line, is shortened.
         */
        private List<String> footerLines(List<String> entries, float width) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String entry : entries) {
                String candidate = line.isEmpty() ? entry : line + " · " + entry;
                if (line.isEmpty() || canvas.width(candidate, Weight.REGULAR, 7.5f) <= width) { line.setLength(0); line.append(candidate); }
                else { lines.add(line.toString()); line.setLength(0); line.append(entry); }
            }
            if (!line.isEmpty()) lines.add(line.toString());
            if (lines.size() > 2) lines = new ArrayList<>(List.of(lines.get(0), String.join(" · ", lines.subList(1, lines.size()))));
            return lines.stream().map(text -> canvas.fit(text, Weight.REGULAR, 7.5f, width)).toList();
        }

        // ------------------------------------------------------------------ helpers

        private void label(String text, float x, float base) throws IOException {
            canvas.text(text, Weight.SEMIBOLD, 6.8f, theme.muted(), x, base, 0.8f);
        }

        private void labelRight(String text, float right, float base) throws IOException {
            canvas.textRight(text, Weight.SEMIBOLD, 6.8f, theme.muted(), right, base, 0.8f);
        }

        private String money(BigDecimal value) { return format(value, digits, digits); }

        private static void addIfDistinct(List<String> lines, String value, String name) {
            if (value != null && !value.isBlank() && !value.equals(name)) lines.add(value);
        }

        private static List<String> concat(List<String> first, List<String> second) {
            return Stream.concat(first.stream(), second.stream()).toList();
        }
    }

    static String format(BigDecimal value, int minDigits, int maxDigits) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('’');
        symbols.setDecimalSeparator('.');
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        format.setMinimumFractionDigits(minDigits);
        format.setMaximumFractionDigits(maxDigits);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    static String quantity(BigDecimal value) { return format(value, 0, 3); }

    static String rate(BigDecimal value) { return format(value, 0, 2) + " %"; }

    static String unit(ServiceUnit unit) {
        return switch (unit) {
            case HOUR -> "Std.";
            case DAY -> "Tag(e)";
            case FIXED -> "pauschal";
            case PIECE -> "Stk.";
            case MONTH -> "Mt.";
        };
    }

    private static String join(String separator, String... values) {
        return String.join(separator, Arrays.stream(values).filter(value -> value != null && !value.isBlank()).toList());
    }
}
