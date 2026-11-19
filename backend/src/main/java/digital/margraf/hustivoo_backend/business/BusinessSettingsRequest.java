package digital.margraf.hustivoo_backend.business;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Editable business settings. All values are normalised in the constructor (trimmed, blank → null,
 * IBAN compact upper case, Swiss UID in its official notation, colour upper case) and then validated.
 * Validation errors are reported per field; the currency is deliberately not part of this request.
 */
public record BusinessSettingsRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = SINGLE_LINE) String name,
        @Size(max = 120) @Pattern(regexp = SINGLE_LINE) String legalForm,
        @Size(max = 120) @Pattern(regexp = SINGLE_LINE) String industry,
        @Size(max = 255) @Pattern(regexp = SINGLE_LINE) String street,
        @Size(max = 20) @Pattern(regexp = "[\\p{L}\\p{N}][\\p{L}\\p{N} -]*") String postalCode,
        @Size(max = 120) @Pattern(regexp = SINGLE_LINE) String city,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String country,
        @Size(max = 320) @Pattern(regexp = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+") String email,
        @Size(max = 40) @Pattern(regexp = "\\+?[0-9][0-9 ()./-]*") String phone,
        @Size(max = 255) @Pattern(regexp = "(?i)(https?://)?([a-z0-9](-*[a-z0-9])*\\.)+[a-z]{2,63}(:[0-9]{1,5})?(/\\S*)?") String website,
        @Size(max = 40) String vatNumber,
        @Size(max = 34) String iban,
        @Size(max = 120) @Pattern(regexp = SINGLE_LINE) String bankName,
        @Size(max = 160) @Pattern(regexp = SINGLE_LINE) String documentFooter,
        @Pattern(regexp = "#[0-9A-F]{6}") String brandColor) {

    static final String SINGLE_LINE = "[^\\p{Cntrl}]*";
    private static final java.util.regex.Pattern SWISS_UID =
            java.util.regex.Pattern.compile("CHE([0-9]{9})(MWST|TVA|IVA)?");
    private static final java.util.regex.Pattern OTHER_VAT = java.util.regex.Pattern.compile("[A-Z]{2}[A-Z0-9 .\\-/]{2,38}");
    /** Lengths for the countries Hustivoo offers during onboarding; others are checked by checksum only. */
    private static final Map<String, Integer> IBAN_LENGTHS = Map.ofEntries(Map.entry("CH", 21), Map.entry("LI", 21),
            Map.entry("DE", 22), Map.entry("AT", 20), Map.entry("FR", 27), Map.entry("IT", 27), Map.entry("NL", 18),
            Map.entry("BE", 16), Map.entry("LU", 20), Map.entry("ES", 24), Map.entry("PT", 25), Map.entry("GB", 22));

    public BusinessSettingsRequest {
        name = name == null ? null : name.strip();
        legalForm = text(legalForm);
        industry = text(industry);
        street = text(street);
        postalCode = text(postalCode);
        city = text(city);
        country = country == null ? null : country.strip().toUpperCase(Locale.ROOT);
        email = text(email);
        phone = text(phone);
        website = text(website);
        vatNumber = vat(text(vatNumber));
        iban = iban == null || iban.isBlank() ? null : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        bankName = text(bankName);
        documentFooter = text(documentFooter);
        brandColor = brandColor == null || brandColor.isBlank() ? null : brandColor.strip().toUpperCase(Locale.ROOT);
    }

    private static String text(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    /** "che 123 456 789 mwst" → "CHE-123.456.789 MWST"; other numbers are only upper-cased. */
    static String vat(String value) {
        if (value == null) return null;
        String upper = value.toUpperCase(Locale.ROOT);
        Matcher swiss = SWISS_UID.matcher(upper.replaceAll("[\\s.\\-]", ""));
        if (!swiss.matches()) return upper;
        String digits = swiss.group(1);
        String formatted = "CHE-" + digits.substring(0, 3) + "." + digits.substring(3, 6) + "." + digits.substring(6);
        return swiss.group(2) == null ? formatted : formatted + " " + swiss.group(2);
    }

    @AssertTrue public boolean isCountryValid() {
        return country == null || Arrays.asList(Locale.getISOCountries()).contains(country);
    }

    @AssertTrue public boolean isPhoneValid() {
        if (phone == null) return true;
        long digits = phone.chars().filter(Character::isDigit).count();
        return digits >= 5 && digits <= 15;
    }

    @AssertTrue public boolean isVatNumberValid() {
        if (vatNumber == null) return true;
        Matcher swiss = SWISS_UID.matcher(vatNumber.replaceAll("[\\s.\\-]", ""));
        if (swiss.matches()) return swissUidChecksum(swiss.group(1));
        return !vatNumber.startsWith("CHE") && OTHER_VAT.matcher(vatNumber).matches();
    }

    /** eCH-0097 check digit: weights 5 4 3 2 7 6 5 4, modulus 11; a remainder yielding 10 is never issued. */
    static boolean swissUidChecksum(String digits) {
        int[] weights = {5, 4, 3, 2, 7, 6, 5, 4};
        int sum = 0;
        for (int index = 0; index < 8; index++) sum += (digits.charAt(index) - '0') * weights[index];
        int check = 11 - sum % 11;
        if (check == 11) check = 0;
        return check != 10 && check == digits.charAt(8) - '0';
    }

    @AssertTrue public boolean isIbanValid() {
        if (iban == null) return true;
        if (!iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")) return false;
        Integer length = IBAN_LENGTHS.get(iban.substring(0, 2));
        if (length != null && iban.length() != length) return false;
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char character : rearranged.toCharArray()) numeric.append(Character.getNumericValue(character));
        return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    /** The accent colour prints the document label, so it must stay readable on white (WCAG AA, 4.5:1). */
    @AssertTrue public boolean isBrandColorValid() {
        if (brandColor == null || !brandColor.matches("#[0-9A-F]{6}")) return true;
        return contrastOnWhite(brandColor) >= 4.5;
    }

    static double contrastOnWhite(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double luminance = 0.2126 * channel(rgb >> 16) + 0.7152 * channel(rgb >> 8) + 0.0722 * channel(rgb);
        return 1.05 / (luminance + 0.05);
    }

    private static double channel(int value) {
        double c = (value & 0xFF) / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
