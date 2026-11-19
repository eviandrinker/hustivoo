package digital.margraf.hustivoo_backend.document;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Neutral content of a priced business document (quote, invoice …) as rendered by
 * {@link BusinessDocumentRenderer}. Callers pass stored values only; the renderer formats but never
 * recalculates amounts.
 *
 * @param id           for log messages only
 * @param label        letter-spaced document label, e.g. "ANGEBOT"
 * @param title        document word used in running headers and footers, e.g. "Angebot"
 * @param number       document number, e.g. "ANG-0001"
 * @param stamp        optional status marker under the number, e.g. "ENTWURF"
 * @param detailsLabel heading of the details block, e.g. "ANGEBOTSDETAILS"
 * @param details      label/value rows; the first value is emphasised
 * @param subject      large heading above the introduction
 * @param totalNote    small line below the total, e.g. validity or due date
 * @param slip         optional detachable part at the bottom of the last page (QR payment part)
 */
public record BusinessDocument(UUID id, String label, String title, String number, String stamp, Recipient recipient,
        String detailsLabel, List<Detail> details, String subject, String introduction, String closing, String currency,
        List<Item> items, BigDecimal subtotal, BigDecimal taxTotal, BigDecimal grandTotal, String totalNote, Slip slip) {

    public BusinessDocument {
        Objects.requireNonNull(label); Objects.requireNonNull(title); Objects.requireNonNull(number);
        Objects.requireNonNull(recipient); Objects.requireNonNull(currency);
        details = List.copyOf(details);
        items = List.copyOf(items);
    }

    /** Recipient snapshot; null or blank values are omitted. */
    public record Recipient(String name, String companyName, String contactName, String street, String postalCode,
            String city, String country, String customerNumber) {}

    public record Detail(String label, String value) {}

    /** One position; {@code quantity} is already formatted with its unit, e.g. "1.5 Std.". */
    public record Item(int position, String description, String quantity, BigDecimal unitPrice, BigDecimal taxRate,
            BigDecimal lineSubtotal, BigDecimal lineTax) {}

    /**
     * A part printed flush with the bottom edge of the last page, e.g. the Swiss QR payment part. The
     * renderer reserves {@link #height()} points, moves the footer above it and, if the remaining space
     * on the last page is too small, places it on an additional page.
     */
    public interface Slip {
        float height();
        void draw(PdfCanvas canvas) throws IOException;
    }
}
