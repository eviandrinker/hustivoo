package digital.margraf.hustivoo_backend.document;

import java.util.List;
import java.util.Objects;

/**
 * Sender block of a document. Only data that actually exists is passed in; empty lists and a null logo
 * are simply omitted by the layout. Businesses currently store a name only, so address, contact lines
 * and logo stay empty until the business profile gains those fields.
 *
 * @param name         legal or trading name (required)
 * @param addressLines postal address, one line each
 * @param contactLines e-mail, phone, website, VAT number …
 * @param logo         PNG/JPEG bytes, or null
 */
public record DocumentSender(String name, List<String> addressLines, List<String> contactLines, byte[] logo) {
    public DocumentSender {
        name = Objects.requireNonNull(name).strip();
        addressLines = clean(addressLines);
        contactLines = clean(contactLines);
    }

    public static DocumentSender named(String name) { return new DocumentSender(name, List.of(), List.of(), null); }

    private static List<String> clean(List<String> lines) {
        return lines == null ? List.of() : lines.stream().filter(line -> line != null && !line.isBlank()).map(String::strip).toList();
    }
}
