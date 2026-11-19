package digital.margraf.hustivoo_backend.document;

import digital.margraf.hustivoo_backend.document.DocumentFonts.Weight;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A4 layout shared by quotes and invoices (minimal Swiss business design, see backend README).
 *
 * <p>Layout rules: pages are created lazily (no empty pages); the table header is never left alone at
 * the bottom of a page and repeats on continuation pages; rows are kept together unless a single row is
 * taller than a page; the totals block is kept together with a short closing text; nothing is drawn
 * below the footer zone. An optional {@link BusinessDocument.Slip} is placed flush with the bottom edge
 * of the last page (on an additional page if it does not fit), with that page's footer moved above it.
 * The carry-over ("Übertrag") is the exact sum of printed line amounts.
 */
public final class BusinessDocumentRenderer {
    private static final Logger LOG = LoggerFactory.getLogger(BusinessDocumentRenderer.class);

    // Geometry (points). Content never goes below CONTENT_BOTTOM; the footer lives beneath FOOTER_RULE.
    public static final float LEFT = 62;
    public static final float RIGHT = PdfCanvas.WIDTH - 62;
    public static final float CONTENT_WIDTH = RIGHT - LEFT;
    public static final float TOP = PdfCanvas.HEIGHT - 60;
    public static final float FOOTER_RULE = 62;
    public static final float CONTENT_BOTTOM = FOOTER_RULE + 28;
    private static final float CONTINUATION_TOP = TOP - 44;
    private static final float TEXT_WIDTH = 410;              // comfortable line length for prose
    private static final float CARRY_HEIGHT = 26;             // reserved below rows for "Übertrag"
    private static final float CLOSING_GAP = 32;              // between totals and closing text
    private static final float SLIP_GAP = 8;                  // free space between content and slip footer
    private static final float SLIP_FOOTER = 30;              // single-line footer above the slip

    private BusinessDocumentRenderer() {}

    /**
     * @param homeCountry the sender's country; the recipient's country is printed only when it differs
     */
    public static byte[] render(BusinessDocument content, DocumentSender sender, DocumentTheme theme, String homeCountry) {
        try (PDDocument document = new PDDocument()) {
            DocumentFonts fonts = DocumentFonts.load(document);
            try (PdfCanvas canvas = new PdfCanvas(document, fonts)) {
                new Layout(canvas, content, sender, theme, homeCountry).write();
            }
            if (!fonts.missing().isEmpty()) {
                LOG.warn("{} {}: {} character(s) not covered by the document font were printed as '?': {}", content.title(), content.id(),
                        fonts.missing().size(), fonts.missing().stream().map(cp -> String.format("U+%04X", cp)).collect(Collectors.joining(" ")));
            }
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle(content.title() + " " + content.number());
            info.setSubject(content.title() + " " + content.number() + " für " + content.recipient().name());
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
        private final BusinessDocument doc;
        private final DocumentSender sender;
        private final DocumentTheme theme;
        private final String homeCountry;
        private final int digits;
        private float y;
        private BigDecimal carried = BigDecimal.ZERO.setScale(2);
        private int rowsOnPage;
        private int slipPage = -1;
        /** Documents with a slip use tighter vertical rhythm so short documents keep it on page one. */
        private final boolean compact;
        // Table geometry, sized so that no amount is ever clipped.
        private float descX, descWidth, qtyRight, priceRight, taxRight, amountWidth;

        Layout(PdfCanvas canvas, BusinessDocument doc, DocumentSender sender, DocumentTheme theme, String homeCountry) {
            this.canvas = canvas; this.doc = doc; this.sender = sender; this.theme = theme; this.homeCountry = homeCountry;
            this.digits = DocumentFormat.minorDigits(doc.currency());
            this.compact = doc.slip() != null;
        }

        private float gap(float regular, float tight) { return compact ? tight : regular; }

        void write() throws IOException {
            canvas.newPage();
            letterhead();
            parties();
            subject();
            columns();
            table();
            totals();
            closing();
            slip();
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
                    LOG.warn("{} {}: sender logo could not be drawn and was omitted", doc.title(), doc.id());
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
            canvas.textRight(doc.label(), Weight.BOLD, 12, theme.primary(), RIGHT, right, 1.8f);
            right -= 17;
            canvas.textRight("Nr. " + doc.number(), Weight.REGULAR, 9, theme.muted(), RIGHT, right);
            if (doc.stamp() != null) {
                float width = canvas.width(doc.stamp(), Weight.SEMIBOLD, 6.5f, 0.9f) + 14;
                right -= 23;
                canvas.roundedRect(RIGHT - width, right - 4.5f, width, 14, 7, theme.secondary(), null, 0);
                canvas.text(doc.stamp(), Weight.SEMIBOLD, 6.5f, theme.warning(), RIGHT - width + 7, right, 0.9f);
            }
            y = Math.min(Math.min(base, right) - gap(34, 20), TOP - gap(108, 92));
        }

        private void parties() throws IOException {
            BusinessDocument.Recipient recipient = doc.recipient();
            float top = y;
            float columnWidth = CONTENT_WIDTH / 2 - 16;
            label("EMPFÄNGER", LEFT, top);
            float left = top - 18;
            String name = recipient.name();
            for (String line : canvas.wrap(name, Weight.SEMIBOLD, 10.5f, columnWidth)) {
                canvas.text(line, Weight.SEMIBOLD, 10.5f, theme.text(), LEFT, left);
                left -= 14.5f;
            }
            List<String> lines = new ArrayList<>();
            addIfDistinct(lines, recipient.companyName(), name);
            addIfDistinct(lines, recipient.contactName(), name);
            if (recipient.street() != null && !recipient.street().isBlank()) lines.add(recipient.street());
            String place = join(" ", recipient.postalCode(), recipient.city());
            if (!place.isEmpty()) lines.add(place);
            if (recipient.country() != null && !recipient.country().equals(homeCountry)) {
                lines.add(Locale.of("", recipient.country()).getDisplayCountry(Locale.GERMAN));
            }
            for (String entry : lines) {
                for (String line : canvas.wrap(entry, Weight.REGULAR, 9.5f, columnWidth)) {
                    canvas.text(line, Weight.REGULAR, 9.5f, theme.text(), LEFT, left);
                    left -= 14;
                }
            }
            if (recipient.customerNumber() != null) {
                left -= 6;
                canvas.text("Kundennummer " + recipient.customerNumber(), Weight.REGULAR, 8.5f, theme.muted(), LEFT, left);
            }

            float metaX = RIGHT - 196;
            label(doc.detailsLabel(), metaX, top);
            float right = top - 18;
            boolean first = true;
            for (BusinessDocument.Detail row : doc.details()) {
                canvas.text(row.label(), Weight.REGULAR, 8.5f, theme.muted(), metaX, right);
                canvas.textRight(row.value(), first ? Weight.SEMIBOLD : Weight.REGULAR, 9.5f, theme.text(), RIGHT, right);
                right -= 15.5f;
                first = false;
            }
            y = Math.min(left, right) - gap(36, 22);
        }

        private void subject() throws IOException {
            for (String line : canvas.wrap(doc.subject(), Weight.SEMIBOLD, 17, CONTENT_WIDTH)) {
                ensure(22);
                canvas.text(line, Weight.SEMIBOLD, 17, theme.text(), LEFT, y);
                y -= 22;
            }
            if (doc.introduction() != null) {
                y -= 6;
                prose(doc.introduction());
            }
            y -= gap(22, 12);
        }

        // ------------------------------------------------------------------ positions

        private void columns() {
            List<BusinessDocument.Item> items = doc.items();
            float qtyWidth = Math.max(58, widest(Weight.REGULAR, 9.5f, items.stream().map(BusinessDocument.Item::quantity)) + 14);
            float priceWidth = Math.max(68, widest(Weight.REGULAR, 9.5f, items.stream().map(item -> money(item.unitPrice()))) + 14);
            float taxWidth = Math.max(42, widest(Weight.REGULAR, 9.5f, items.stream().map(item -> DocumentFormat.rate(item.taxRate()))) + 12);
            amountWidth = Math.max(74, widest(Weight.SEMIBOLD, 9.5f, Stream.concat(items.stream().map(item -> money(item.lineSubtotal())),
                    Stream.of(money(doc.subtotal())))) + 14);
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

        private List<Line> lines(BusinessDocument.Item item) {
            String[] parts = item.description().split("\\R", 2);
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
            List<BusinessDocument.Item> items = doc.items();
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
            for (BusinessDocument.Item item : items) {
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
            labelRight("BETRAG " + doc.currency(), RIGHT, base);
            y = base - 9;
            canvas.rule(theme.text(), LEFT, RIGHT, y, 0.6f);
            rowsOnPage = 0;
        }

        /** Draws one position; only a position taller than a whole page is split across pages. */
        private void row(BusinessDocument.Item item, List<Line> lines) throws IOException {
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
                if (segmentStart) canvas.text(String.valueOf(item.position()), Weight.REGULAR, 9, theme.muted(), LEFT, y);
                if (!numbers) {
                    canvas.textRight(item.quantity(), Weight.REGULAR, 9.5f, theme.text(), qtyRight, y);
                    canvas.textRight(money(item.unitPrice()), Weight.REGULAR, 9.5f, theme.text(), priceRight, y);
                    canvas.textRight(DocumentFormat.rate(item.taxRate()), Weight.REGULAR, 9.5f, theme.muted(), taxRight, y);
                    canvas.textRight(money(item.lineSubtotal()), Weight.SEMIBOLD, 9.5f, theme.text(), RIGHT, y);
                    carried = carried.add(item.lineSubtotal());
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

        private List<BigDecimal[]> taxGroups() {
            Map<BigDecimal, BigDecimal[]> groups = new TreeMap<>();
            for (BusinessDocument.Item item : doc.items()) {
                BigDecimal[] sums = groups.computeIfAbsent(item.taxRate().setScale(2),
                        rate -> new BigDecimal[] {rate, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)});
                sums[1] = sums[1].add(item.lineSubtotal());
                sums[2] = sums[2].add(item.lineTax());
            }
            return groups.values().stream().filter(group -> group[0].signum() > 0).toList();
        }

        private void totals() throws IOException {
            List<BigDecimal[]> taxed = taxGroups();
            int rows = 1 + Math.max(1, taxed.size());
            // Exact height: gap, rows, total rule and line, note and its descender.
            float block = gap(26, 14) + rows * 17 - 3 + 24 + 20 + 4;
            // A short closing text stays with the totals instead of ending up alone on an extra page.
            int closingLines = doc.closing() == null ? 0 : wrapProse(doc.closing()).size();
            if (closingLines > 0 && closingLines <= 6) block += gap(CLOSING_GAP, 18) + closingLines * 14.5f;
            if (y - block < CONTENT_BOTTOM) {
                if (!doc.items().isEmpty()) carryOut();
                continuationPage();
                if (!doc.items().isEmpty()) carryIn();
            }
            float labelX = RIGHT - 270;
            String currency = doc.currency() + " ";
            y -= gap(26, 14);
            totalRow("Zwischensumme", currency + money(doc.subtotal()), labelX);
            if (taxed.isEmpty()) totalRow("MWST", currency + money(doc.taxTotal()), labelX);
            for (BigDecimal[] group : taxed) {
                totalRow("MWST " + DocumentFormat.rate(group[0]) + " auf " + money(group[1]), currency + money(group[2]), labelX);
            }
            y += 3;
            canvas.rule(theme.primary(), labelX, RIGHT, y, 1.2f);
            y -= 24;
            canvas.text("Total", Weight.SEMIBOLD, 11, theme.text(), labelX, y);
            canvas.textRight(currency + money(doc.grandTotal()), Weight.BOLD, 15, theme.text(), RIGHT, y);
            y -= 20;
            if (doc.totalNote() != null) canvas.textRight(doc.totalNote(), Weight.REGULAR, 8, theme.muted(), RIGHT, y);
        }

        private void totalRow(String label, String value, float labelX) throws IOException {
            canvas.text(label, Weight.REGULAR, 9, theme.muted(), labelX, y);
            canvas.textRight(value, Weight.REGULAR, 9.5f, theme.text(), RIGHT, y);
            y -= 17;
        }

        private void closing() throws IOException {
            if (doc.closing() == null) return;
            y -= gap(CLOSING_GAP, 18);
            prose(doc.closing());
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

        // ------------------------------------------------------------------ slip

        /** The slip goes to the bottom of the last page; its footer moves above it. */
        private void slip() throws IOException {
            BusinessDocument.Slip slip = doc.slip();
            if (slip == null) return;
            // Lowest ink: prose leaves the cursor one line below its last baseline, totals end on the note's baseline.
            float inkBottom = doc.closing() != null ? y + 14.5f - 3 : y - 3;
            if (inkBottom - SLIP_GAP < slip.height() + SLIP_FOOTER) continuationPage();
            slipPage = canvas.pageCount() - 1;
            slip.draw(canvas);
        }

        // ------------------------------------------------------------------ pages

        private void ensure(float height) throws IOException {
            if (y - height < CONTENT_BOTTOM) continuationPage();
        }

        private void continuationPage() throws IOException {
            canvas.newPage();
            float base = TOP - 2;
            canvas.text(canvas.fit(sender.name(), Weight.SEMIBOLD, 8.5f, CONTENT_WIDTH - 200), Weight.SEMIBOLD, 8.5f, theme.text(), LEFT, base);
            canvas.textRight(doc.title() + " " + doc.number(), Weight.REGULAR, 8.5f, theme.muted(), RIGHT, base);
            canvas.rule(theme.rule(), LEFT, RIGHT, base - 11, 0.5f);
            y = CONTINUATION_TOP;
        }

        private void footers() throws IOException {
            int count = canvas.pageCount();
            // Payment details and the footer note take precedence; without them the address repeats here.
            List<String> entries = concat(sender.paymentLines(), theme.footerNote() == null ? List.of() : List.of(theme.footerNote()));
            if (entries.isEmpty()) entries = concat(sender.addressLines(), sender.contactLines());
            float leftWidth = CONTENT_WIDTH - 150;
            List<String> lines = footerLines(entries, leftWidth, CONTENT_WIDTH);
            boolean details = !lines.isEmpty();
            for (int index = 0; index < count; index++) {
                int page = index + 1;
                if (index == slipPage) {
                    // Above the slip: one compact line; payment details are part of the slip itself.
                    float base = doc.slip().height() + 12;
                    canvas.onPage(index, () -> {
                        canvas.rule(theme.rule(), LEFT, RIGHT, base + 12, 0.5f);
                        canvas.text(canvas.fit(sender.name(), Weight.SEMIBOLD, 7.5f, CONTENT_WIDTH - 220), Weight.SEMIBOLD, 7.5f, theme.text(), LEFT, base);
                        canvas.textRight(doc.title() + " " + doc.number() + "  ·  Seite " + page + " von " + count,
                                Weight.REGULAR, 7.5f, theme.muted(), RIGHT, base);
                    });
                    continue;
                }
                float lift = 0;
                canvas.onPage(index, () -> {
                    canvas.rule(theme.rule(), LEFT, RIGHT, lift + FOOTER_RULE, 0.5f);
                    canvas.text(canvas.fit(sender.name(), Weight.SEMIBOLD, 7.5f, leftWidth), Weight.SEMIBOLD, 7.5f, theme.text(), LEFT, lift + (details ? 46 : 42));
                    for (int line = 0; line < lines.size(); line++) {
                        canvas.text(lines.get(line), Weight.REGULAR, 7.5f, theme.muted(), LEFT, lift + 35 - line * 10);
                    }
                    canvas.textRight(doc.title() + " " + doc.number(), Weight.REGULAR, 7.5f, theme.muted(), RIGHT, lift + 46);
                    canvas.textRight("Seite " + page + " von " + count, Weight.REGULAR, 7.5f, theme.muted(), RIGHT, lift + 35);
                });
            }
        }

        /**
         * Footer details on at most two lines: the first shares its row with the page number, the second
         * spans the full width. Entries (payment details, footer note, address) are never split between
         * lines; only an entry longer than a whole line, or what would need a third line, is shortened.
         */
        private List<String> footerLines(List<String> entries, float firstWidth, float secondWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String entry : entries) {
                String candidate = line.isEmpty() ? entry : line + " · " + entry;
                float width = lines.isEmpty() ? firstWidth : secondWidth;
                if (line.isEmpty() || canvas.width(candidate, Weight.REGULAR, 7.5f) <= width) { line.setLength(0); line.append(candidate); }
                else { lines.add(line.toString()); line.setLength(0); line.append(entry); }
            }
            if (!line.isEmpty()) lines.add(line.toString());
            if (lines.size() > 2) lines = new ArrayList<>(List.of(lines.get(0), String.join(" · ", lines.subList(1, lines.size()))));
            List<String> fitted = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                fitted.add(canvas.fit(lines.get(index), Weight.REGULAR, 7.5f, index == 0 ? firstWidth : secondWidth));
            }
            return fitted;
        }

        // ------------------------------------------------------------------ helpers

        private void label(String text, float x, float base) throws IOException {
            canvas.text(text, Weight.SEMIBOLD, 6.8f, theme.muted(), x, base, 0.8f);
        }

        private void labelRight(String text, float right, float base) throws IOException {
            canvas.textRight(text, Weight.SEMIBOLD, 6.8f, theme.muted(), right, base, 0.8f);
        }

        private String money(BigDecimal value) { return DocumentFormat.number(value, digits, digits); }

        private static void addIfDistinct(List<String> lines, String value, String name) {
            if (value != null && !value.isBlank() && !value.equals(name)) lines.add(value);
        }

        private static List<String> concat(List<String> first, List<String> second) {
            return Stream.concat(first.stream(), second.stream()).toList();
        }

        private static String join(String separator, String... values) {
            return String.join(separator, Arrays.stream(values).filter(value -> value != null && !value.isBlank()).toList());
        }
    }

    /** Swiss number formatting for documents: "1’234.50". */
    public static final class DocumentFormat {
        private DocumentFormat() {}

        public static String number(BigDecimal value, int minDigits, int maxDigits) {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
            symbols.setGroupingSeparator('’');
            symbols.setDecimalSeparator('.');
            DecimalFormat format = new DecimalFormat("#,##0", symbols);
            format.setMinimumFractionDigits(minDigits);
            format.setMaximumFractionDigits(maxDigits);
            format.setRoundingMode(RoundingMode.HALF_UP);
            return format.format(value);
        }

        public static String quantity(BigDecimal value) { return number(value, 0, 3); }

        public static String rate(BigDecimal value) { return number(value, 0, 2) + " %"; }

        public static int minorDigits(String currency) {
            return Math.max(0, Math.min(2, Currency.getInstance(currency).getDefaultFractionDigits()));
        }
    }
}
