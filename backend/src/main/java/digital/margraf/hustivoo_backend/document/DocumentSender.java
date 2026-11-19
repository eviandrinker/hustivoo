package digital.margraf.hustivoo_backend.document;

import java.util.List;
import java.util.Objects;

/**
 * Sender block of a document. Only data that actually exists is passed in; empty lists and a null logo
 * are simply omitted by the layout.
 *
 * @param name         legal or trading name (required)
 * @param addressLines postal address, one line each
 * @param contactLines e-mail, phone, website, VAT number …
 * @param paymentLines bank details (IBAN, bank); printed in the footer of every page
 * @param logo         PNG/JPEG bytes, or null
 */
public record DocumentSender(String name, List<String> addressLines, List<String> contactLines, List<String> paymentLines, byte[] logo) {
    public DocumentSender {
        name = Objects.requireNonNull(name).strip();
        addressLines = clean(addressLines);
        contactLines = clean(contactLines);
        paymentLines = clean(paymentLines);
    }

    public DocumentSender(String name, List<String> addressLines, List<String> contactLines, byte[] logo) {
        this(name, addressLines, contactLines, List.of(), logo);
    }

    public static DocumentSender named(String name) { return new DocumentSender(name, List.of(), List.of(), List.of(), null); }

    private static List<String> clean(List<String> lines) {
        return lines == null ? List.of() : lines.stream().filter(line -> line != null && !line.isBlank()).map(String::strip).toList();
    }
}
