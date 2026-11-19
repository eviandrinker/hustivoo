package digital.margraf.hustivoo_backend.document.qrbill;

import digital.margraf.hustivoo_backend.document.BusinessDocument;
import digital.margraf.hustivoo_backend.document.PdfCanvas;
import io.nayuki.qrcodegen.QrCode;
import io.nayuki.qrcodegen.QrSegment;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Payment part with receipt (210 × 105 mm) at the bottom of an A4 page, following the QR-bill
 * Implementation Guidelines v2.4 chapter 3 and the SIX Style Guide:
 * <ul>
 *   <li>receipt 62 mm wide, payment part 148 mm, 5 mm margins, separated by lines with a scissors symbol
 *       and the hint "Vor der Einzahlung abzutrennen" above the slip (PDF variant, IG 3.7)</li>
 *   <li>Liberation Sans (permitted font, SIL OFL, embedded) in black; titles 11 pt bold; payment part
 *       headings 8 pt bold / values 10 pt (line spacing 11 pt); receipt 6 pt / 8 pt (9 pt)</li>
 *   <li>Swiss QR Code 46 × 46 mm at 17 mm from the top, error correction level M, version ≤ 25, byte mode,
 *       drawn as vectors with the 7 × 7 mm Swiss cross</li>
 *   <li>without debtor: heading "Zahlbar durch (Name/Adresse)" and corner-marked fields 65 × 25 mm /
 *       52 × 20 mm (0.75 pt)</li>
 * </ul>
 */
public final class QrBillSlip implements BusinessDocument.Slip {
    public static final float MM = 72f / 25.4f;
    public static final float SLIP_HEIGHT = 105 * MM;
    static final float RECEIPT_WIDTH = 62 * MM;
    static final float QR_SIZE = 46 * MM;
    static final float QR_TOP = 17 * MM;          // from the top edge of the slip
    static final float AMOUNT_TOP = 68 * MM;
    private static final float HINT_SPACE = 4 * MM;
    private static final Map<String, byte[]> FONT_CACHE = new ConcurrentHashMap<>();

    private final SwissQrBill bill;
    private PDFont regular;
    private PDFont bold;
    private PDPageContentStream stream;

    public QrBillSlip(SwissQrBill bill) { this.bill = bill; }

    public SwissQrBill bill() { return bill; }

    @Override public float height() { return SLIP_HEIGHT + HINT_SPACE; }

    @Override
    public void draw(PdfCanvas canvas) throws IOException {
        regular = PDType0Font.load(canvas.document(), new ByteArrayInputStream(font("LiberationSans-Regular.ttf")), true);
        bold = PDType0Font.load(canvas.document(), new ByteArrayInputStream(font("LiberationSans-Bold.ttf")), true);
        stream = canvas.stream();
        stream.saveGraphicsState();
        separators();
        receipt();
        paymentPart();
        stream.restoreGraphicsState();
    }

    // ------------------------------------------------------------------ separators

    private void separators() throws IOException {
        float width = PdfCanvas.WIDTH;
        stream.setStrokingColor(Color.BLACK);
        stream.setLineWidth(0.5f);
        stream.setLineDashPattern(new float[] {2.5f, 1.5f}, 0);
        stream.moveTo(0, SLIP_HEIGHT); stream.lineTo(width, SLIP_HEIGHT);
        stream.moveTo(RECEIPT_WIDTH, 0); stream.lineTo(RECEIPT_WIDTH, SLIP_HEIGHT);
        stream.stroke();
        stream.setLineDashPattern(new float[] {}, 0);
        String hint = "Vor der Einzahlung abzutrennen";
        float size = 7;
        text(hint, regular, size, (width - width(hint, regular, size)) / 2, SLIP_HEIGHT + 1.5f * MM);
        scissors(10 * MM, SLIP_HEIGHT, false);
        scissors(RECEIPT_WIDTH, SLIP_HEIGHT - 12 * MM, true);
    }

    /** Small vector scissors centred on a separator line. */
    private void scissors(float x, float y, boolean vertical) throws IOException {
        stream.saveGraphicsState();
        stream.transform(new org.apache.pdfbox.util.Matrix(1, 0, 0, 1, x, y));
        if (vertical) stream.transform(new org.apache.pdfbox.util.Matrix(0, -1, 1, 0, 0, 0));
        // White gap so the dashed line does not cross the symbol.
        stream.setNonStrokingColor(Color.WHITE);
        stream.addRect(-2.6f * MM, -1.7f * MM, 5.2f * MM, 3.4f * MM);
        stream.fill();
        stream.setStrokingColor(Color.BLACK);
        stream.setLineWidth(0.6f);
        float r = 0.75f * MM;
        circle(-1.6f * MM, 0.9f * MM, r);
        circle(-1.6f * MM, -0.9f * MM, r);
        stream.moveTo(-0.95f * MM, 0.55f * MM); stream.lineTo(2.4f * MM, -1.1f * MM);
        stream.moveTo(-0.95f * MM, -0.55f * MM); stream.lineTo(2.4f * MM, 1.1f * MM);
        stream.stroke();
        stream.restoreGraphicsState();
    }

    private void circle(float cx, float cy, float r) throws IOException {
        float k = 0.5523f * r;
        stream.moveTo(cx + r, cy);
        stream.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r);
        stream.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy);
        stream.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r);
        stream.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy);
        stream.stroke();
    }

    // ------------------------------------------------------------------ receipt

    private void receipt() throws IOException {
        float x = 5 * MM;
        float width = 52 * MM;
        text("Empfangsschein", bold, 11, x, top(5 * MM) - 11 * 0.8f);
        Block block = new Block(x, width, top(12 * MM), 6, 8, 9);
        block.heading("Konto / Zahlbar an");
        block.value(bill.formattedIban());
        creditorLines(block);
        block.gap();
        if (bill.debtor() != null) {
            block.heading("Zahlbar durch");
            debtorLines(block);
        } else {
            block.heading("Zahlbar durch (Name/Adresse)");
            cornerBox(x, block.y - 1 * MM - 20 * MM, 52 * MM, 20 * MM);
        }
        float amountTop = top(AMOUNT_TOP);
        text("Währung", bold, 6, x, amountTop - 6);
        text("Betrag", bold, 6, x + 12 * MM, amountTop - 6);
        text(bill.currency(), regular, 8, x, amountTop - 6 - 11);
        text(bill.formattedAmount(), regular, 8, x + 12 * MM, amountTop - 6 - 11);
        String acceptance = "Annahmestelle";
        text(acceptance, bold, 6, RECEIPT_WIDTH - 5 * MM - width(acceptance, bold, 6), top(82 * MM) - 6);
        if (block.y < amountTop) throw new IllegalStateException("Receipt information overflows");
    }

    // ------------------------------------------------------------------ payment part

    private void paymentPart() throws IOException {
        float x = RECEIPT_WIDTH + 5 * MM;
        text("Zahlteil", bold, 11, x, top(5 * MM) - 11 * 0.8f);
        qrCode(x, top(QR_TOP));
        float amountTop = top(AMOUNT_TOP);
        text("Währung", bold, 8, x, amountTop - 8);
        text("Betrag", bold, 8, x + 14 * MM, amountTop - 8);
        text(bill.currency(), regular, 10, x, amountTop - 8 - 13);
        text(bill.formattedAmount(), regular, 10, x + 14 * MM, amountTop - 8 - 13);

        float infoX = RECEIPT_WIDTH + 56 * MM;
        Block block = new Block(infoX, PdfCanvas.WIDTH - 5 * MM - infoX, top(5 * MM), 8, 10, 11);
        block.heading("Konto / Zahlbar an");
        block.value(bill.formattedIban());
        creditorLines(block);
        if (bill.message() != null) {
            block.gap();
            block.heading("Zusätzliche Informationen");
            block.wrapped(bill.message());
        }
        block.gap();
        if (bill.debtor() != null) {
            block.heading("Zahlbar durch");
            debtorLines(block);
        } else {
            block.heading("Zahlbar durch (Name/Adresse)");
            cornerBox(infoX, block.y - 1 * MM - 25 * MM, 65 * MM, 25 * MM);
            block.y -= 26 * MM;
        }
        if (block.y < 5 * MM) throw new IllegalStateException("Payment part information overflows");
    }

    private void creditorLines(Block block) throws IOException {
        SwissQrBill.Address creditor = bill.creditor();
        block.wrapped(creditor.name());
        if (!creditor.streetLine().isEmpty()) block.wrapped(creditor.streetLine());
        block.wrapped(creditor.placeLine());
    }

    private void debtorLines(Block block) throws IOException {
        SwissQrBill.Address debtor = bill.debtor();
        block.wrapped(debtor.name());
        if (!debtor.streetLine().isEmpty()) block.wrapped(debtor.streetLine());
        block.wrapped(debtor.placeLine());
    }

    /** Swiss QR Code: smallest version at level M (max. 25), scaled to 46 mm, with the Swiss cross. */
    private void qrCode(float x, float top) throws IOException {
        QrCode code = encode(bill.payload());
        int size = code.size;
        float module = QR_SIZE / size;
        float bottom = top - QR_SIZE;
        stream.setNonStrokingColor(Color.BLACK);
        for (int row = 0; row < size; row++) {
            int column = 0;
            while (column < size) {
                if (!code.getModule(column, row)) { column++; continue; }
                int start = column;
                while (column < size && code.getModule(column, row)) column++;
                // Slight overlap avoids hairline gaps between adjacent modules in some viewers.
                stream.addRect(x + start * module, top - (row + 1) * module, (column - start) * module + 0.01f, module + 0.01f);
            }
        }
        stream.fill();
        swissCross(x + QR_SIZE / 2, bottom + QR_SIZE / 2);
    }

    static QrCode encode(String payload) {
        List<QrSegment> segments = List.of(QrSegment.makeBytes(payload.getBytes(StandardCharsets.UTF_8)));
        return QrCode.encodeSegments(segments, QrCode.Ecc.MEDIUM, 1, 25, -1, false);
    }

    /** 7 × 7 mm recognition symbol: white margin, black square, white cross in Swiss flag proportions. */
    private void swissCross(float cx, float cy) throws IOException {
        float outer = 7 * MM;
        float inner = 6 * MM;
        stream.setNonStrokingColor(Color.WHITE);
        stream.addRect(cx - outer / 2, cy - outer / 2, outer, outer);
        stream.fill();
        stream.setNonStrokingColor(Color.BLACK);
        stream.addRect(cx - inner / 2, cy - inner / 2, inner, inner);
        stream.fill();
        float arm = inner * 6 / 32 * 1.0f;       // bar width
        float length = inner * 20 / 32;          // bar length
        stream.setNonStrokingColor(Color.WHITE);
        stream.addRect(cx - arm / 2, cy - length / 2, arm, length);
        stream.addRect(cx - length / 2, cy - arm / 2, length, arm);
        stream.fill();
        stream.setNonStrokingColor(Color.BLACK);
    }

    /** Corner marks of a blank field: 3 mm arms, 0.75 pt. */
    private void cornerBox(float x, float y, float width, float height) throws IOException {
        float arm = 3 * MM;
        stream.setStrokingColor(Color.BLACK);
        stream.setLineWidth(0.75f);
        stream.moveTo(x, y + arm); stream.lineTo(x, y); stream.lineTo(x + arm, y);
        stream.moveTo(x + width - arm, y); stream.lineTo(x + width, y); stream.lineTo(x + width, y + arm);
        stream.moveTo(x + width, y + height - arm); stream.lineTo(x + width, y + height); stream.lineTo(x + width - arm, y + height);
        stream.moveTo(x + arm, y + height); stream.lineTo(x, y + height); stream.lineTo(x, y + height - arm);
        stream.stroke();
    }

    // ------------------------------------------------------------------ text

    /** A column of headings and values; blocks are separated by one blank line (Style Guide). */
    private final class Block {
        final float x, width, headingSize, valueSize, leading;
        float y;
        boolean first = true;

        Block(float x, float width, float top, float headingSize, float valueSize, float leading) {
            this.x = x; this.width = width; this.headingSize = headingSize; this.valueSize = valueSize; this.leading = leading;
            this.y = top;
        }

        void heading(String value) throws IOException {
            y -= first ? headingSize : leading;
            first = false;
            text(value, bold, headingSize, x, y);
        }

        void value(String value) throws IOException {
            y -= leading;
            text(value, regular, valueSize, x, y);
        }

        void wrapped(String value) throws IOException {
            for (String line : wrap(value, regular, valueSize, width)) value(line);
        }

        void gap() { y -= leading * 0.6f; }
    }

    private float top(float fromTop) { return SLIP_HEIGHT - fromTop; }

    private void text(String value, PDFont font, float size, float x, float baseline) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.setNonStrokingColor(Color.BLACK);
        stream.newLineAtOffset(x, baseline);
        stream.showText(value);
        stream.endText();
    }

    private static float width(String value, PDFont font, float size) {
        try { return font.getStringWidth(value) / 1000 * size; }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    static List<String> wrap(String value, PDFont font, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.strip().split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || width(candidate, font, size) <= maxWidth) { line.setLength(0); line.append(candidate); }
            else { lines.add(line.toString()); line.setLength(0); line.append(word); }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private static byte[] font(String file) {
        return FONT_CACHE.computeIfAbsent(file, name -> {
            try (InputStream in = QrBillSlip.class.getResourceAsStream("/fonts/liberation/" + name)) {
                if (in == null) throw new IllegalStateException("Missing bundled font " + name);
                return in.readAllBytes();
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        });
    }
}
