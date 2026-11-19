package digital.margraf.hustivoo_backend.business;

import digital.margraf.hustivoo_backend.document.DocumentSender;
import digital.margraf.hustivoo_backend.document.DocumentTheme;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Everything a business document prints about its sender. Built from the current settings (drafts)
 * or from the snapshot stored on an issued document; only stored values are ever printed.
 */
public record SenderProfile(String name, String country, String street, String postalCode, String city,
        String email, String phone, String website, String vatNumber, String iban, String bankName,
        String documentFooter, String brandColor, UUID logoId) {

    public SenderProfile {
        Objects.requireNonNull(name);
        Objects.requireNonNull(country);
    }

    public static SenderProfile of(Business business) {
        return new SenderProfile(business.getName(), business.getCountry(), business.getStreet(), business.getPostalCode(),
                business.getCity(), business.getEmail(), business.getPhone(), business.getWebsite(), business.getVatNumber(),
                business.getIban(), business.getBankName(), business.getDocumentFooter(), business.getBrandColor(), business.getLogoId());
    }

    /**
     * @param logo             PNG bytes of {@link #logoId()}, or null
     * @param recipientCountry the sender's country is printed only when it differs from the recipient's
     */
    public DocumentSender document(byte[] logo, String recipientCountry) {
        // One compact letterhead line: "Seestrasse 12 · 8002 Zürich" (plus the country for foreign recipients).
        String place = join(" ", postalCode, city);
        String foreign = street == null && place.isEmpty() || country.equals(recipientCountry) ? null
                : Locale.of("", country).getDisplayCountry(Locale.GERMAN);
        String address = join(" · ", street, place, foreign);
        List<String> contact = Stream.of(join(" · ", email, phone), join(" · ", website, vat()))
                .filter(line -> !line.isEmpty()).toList();
        List<String> payment = new ArrayList<>();
        if (iban != null) payment.add("IBAN " + formatIban(iban));
        if (bankName != null) payment.add(bankName);
        return new DocumentSender(name, address.isEmpty() ? List.of() : List.of(address), contact, payment, logo);
    }

    public DocumentTheme theme() {
        DocumentTheme theme = DocumentTheme.hustivoo().withFooterNote(documentFooter);
        if (brandColor == null) return theme;
        Color primary = new Color(Integer.parseInt(brandColor.substring(1), 16));
        return theme.withColors(primary, tint(primary, 0.1f));
    }

    private String vat() {
        if (vatNumber == null) return null;
        return vatNumber.startsWith("CHE-") ? "UID " + vatNumber : "MWST-Nr. " + vatNumber;
    }

    /** "CH9300762011623852957" → "CH93 0076 2011 6238 5295 7" */
    public static String formatIban(String compact) {
        return compact.replaceAll("(.{4})(?!$)", "$1 ");
    }

    /** Mixes the colour into white; used for subtle backgrounds such as the draft marker. */
    static Color tint(Color color, float amount) {
        return new Color(Math.round(255 - (255 - color.getRed()) * amount), Math.round(255 - (255 - color.getGreen()) * amount),
                Math.round(255 - (255 - color.getBlue()) * amount));
    }

    private static String join(String separator, String... values) {
        return Stream.of(values).filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(separator));
    }
}
