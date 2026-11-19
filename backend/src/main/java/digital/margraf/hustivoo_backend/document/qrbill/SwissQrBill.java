package digital.margraf.hustivoo_backend.document.qrbill;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validated content of a Swiss QR-bill according to the Swiss Implementation Guidelines for the
 * QR-bill, version 2.4 (SIX, valid from 14 November 2026; data structure version "0200").
 *
 * <p>Supported: IBAN (not QR-IBAN) with reference type {@code NON} and an unstructured message, in CHF
 * or EUR, structured addresses (type "S") only. QR-IBAN/QRR and SCOR are deliberately not supported:
 * a QR reference requires a QR-IBAN agreement with the bank, which Hustivoo does not manage.
 *
 * <p>Instances can only be created through {@link #create}, which returns a list of German problems
 * instead of a bill whenever a rule is violated, so an invalid QR code can never be produced.
 */
public final class SwissQrBill {
    public static final String NOT_FOR_PAYMENT = "NICHT ZUR ZAHLUNG VERWENDEN";
    static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");
    private static final Pattern BUILDING_NUMBER =
            Pattern.compile("^(.*?\\S)\\s+(\\d{1,5}\\s?[a-zA-Z]?(?:\\s?[-/]\\s?\\d{1,5}\\s?[a-zA-Z]?)?)$");

    /** Structured address ("S"). Street and building number are optional; postal code and town are not. */
    public record Address(String name, String street, String buildingNumber, String postalCode, String town, String country) {
        /**
         * Builds an address from a single street line ("Seestrasse 12" → street "Seestrasse", number "12").
         * Lines without a trailing number stay complete in the street element (allowed by IG 4.3.1).
         */
        public static Address of(String name, String streetLine, String postalCode, String town, String country) {
            String street = blankToNull(streetLine);
            String number = null;
            if (street != null) {
                Matcher matcher = BUILDING_NUMBER.matcher(street);
                if (matcher.matches()) { street = matcher.group(1); number = matcher.group(2).replaceAll("\\s+", ""); }
            }
            return new Address(blankToNull(name), street, number, blankToNull(postalCode), blankToNull(town),
                    country == null ? null : country.strip().toUpperCase(Locale.ROOT));
        }

        /** "Seestrasse 12" */
        public String streetLine() { return join(" ", street, buildingNumber); }

        /** "8002 Zürich", prefixed with the country code for addresses outside Switzerland (IG 3.5.4). */
        public String placeLine() {
            String place = join(" ", postalCode, town);
            return "CH".equals(country) ? place : country + " – " + place;
        }
    }

    private final String iban;
    private final Address creditor;
    private final BigDecimal amount;
    private final String currency;
    private final Address debtor;
    private final String message;

    private SwissQrBill(String iban, Address creditor, BigDecimal amount, String currency, Address debtor, String message) {
        this.iban = iban; this.creditor = creditor; this.amount = amount; this.currency = currency;
        this.debtor = debtor; this.message = message;
    }

    /** Either a bill or the reasons why none can be produced. */
    public record Result(SwissQrBill bill, List<String> problems) {
        public boolean valid() { return bill != null; }
    }

    /**
     * @param iban       creditor account (spaces allowed), must be a CH/LI IBAN, not a QR-IBAN
     * @param creditor   must match the account holder; name, postal code, town and country are mandatory
     * @param amount     0.01–999 999 999.99, or exactly 0.00 together with {@link #NOT_FOR_PAYMENT}
     * @param debtor     optional; an incomplete or unsupported debtor is omitted (the bill then shows a
     *                   blank "Zahlbar durch (Name/Adresse)" field, which the guidelines allow)
     * @param message    unstructured message, max. 140 characters
     */
    public static Result create(String iban, Address creditor, BigDecimal amount, String currency, Address debtor, String message) {
        List<String> problems = new ArrayList<>();
        String account = iban == null ? null : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (account == null || account.isEmpty()) {
            problems.add("In den Unternehmenseinstellungen ist keine IBAN hinterlegt.");
        } else if (!ibanValid(account)) {
            problems.add("Die IBAN in den Unternehmenseinstellungen ist ungültig.");
        } else if (!account.startsWith("CH") && !account.startsWith("LI")) {
            problems.add("Für die QR-Rechnung ist eine Schweizer oder Liechtensteiner IBAN (CH/LI) erforderlich.");
        } else if (qrIban(account)) {
            problems.add("Die hinterlegte IBAN ist eine QR-IBAN. Sie erfordert eine QR-Referenz, die Hustivoo noch nicht unterstützt. Bitte hinterlege die normale IBAN des Kontos.");
        }
        if (!"CHF".equals(currency) && !"EUR".equals(currency)) {
            problems.add("QR-Rechnungen sind nur in CHF oder EUR möglich (Rechnungswährung " + currency + ").");
        }
        boolean notification = NOT_FOR_PAYMENT.equals(message);
        if (amount == null || amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            problems.add("Der Rechnungsbetrag ist ungültig.");
        } else if (notification ? amount.signum() != 0
                : amount.compareTo(new BigDecimal("0.01")) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            problems.add("Der Betrag muss zwischen 0.01 und 999 999 999.99 liegen.");
        }
        problems.addAll(addressProblems(creditor, "Unternehmen"));
        if (message != null && (message.length() > 140 || !supported(message))) {
            problems.add("Die Zahlungsmitteilung ist zu lang oder enthält nicht zulässige Zeichen.");
        }
        if (!problems.isEmpty()) return new Result(null, List.copyOf(problems));
        Address payer = debtor != null && addressProblems(debtor, "Kunde").isEmpty() ? debtor : null;
        return new Result(new SwissQrBill(account, creditor, amount.setScale(2, RoundingMode.UNNECESSARY), currency, payer,
                blankToNull(message)), List.of());
    }

    static List<String> addressProblems(Address address, String owner) {
        List<String> problems = new ArrayList<>();
        if (address == null || address.name() == null) { problems.add(owner + ": Name fehlt."); return problems; }
        if (address.postalCode() == null || address.town() == null) problems.add(owner + ": Postleitzahl und Ort sind für die QR-Rechnung erforderlich.");
        if (address.country() == null || !Arrays.asList(Locale.getISOCountries()).contains(address.country())) problems.add(owner + ": Land fehlt oder ist ungültig.");
        if (length(address.name()) > 70) problems.add(owner + ": Der Name ist für die QR-Rechnung zu lang (max. 70 Zeichen).");
        if (length(address.street()) > 70 || length(address.buildingNumber()) > 16) problems.add(owner + ": Die Strasse ist für die QR-Rechnung zu lang (max. 70 Zeichen).");
        if (length(address.postalCode()) > 16 || length(address.town()) > 35) problems.add(owner + ": Postleitzahl oder Ort sind zu lang (max. 16 bzw. 35 Zeichen).");
        for (String value : new String[] {address.name(), address.street(), address.buildingNumber(), address.postalCode(), address.town()}) {
            if (value != null && !supported(value)) {
                problems.add(owner + ": Die Adresse enthält Zeichen, die in der QR-Rechnung nicht zulässig sind.");
                break;
            }
        }
        return problems;
    }

    /** IG 4.1.1: Basic Latin, Latin-1 Supplement, Latin Extended-A, Ș ș Ț ț and €; no control characters. */
    static boolean supported(String value) {
        return value.codePoints().allMatch(cp -> cp >= 0x20 && cp <= 0x7E || cp >= 0xA0 && cp <= 0x17F
                || cp >= 0x218 && cp <= 0x21B || cp == 0x20AC);
    }

    static boolean ibanValid(String iban) {
        if (!iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")) return false;
        if ((iban.startsWith("CH") || iban.startsWith("LI")) && iban.length() != 21) return false;
        StringBuilder numeric = new StringBuilder();
        for (char character : (iban.substring(4) + iban.substring(0, 4)).toCharArray()) numeric.append(Character.getNumericValue(character));
        return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    /** QR-IBANs carry an institution ID (positions 5–9) in the range 30000–31999. */
    static boolean qrIban(String iban) {
        int iid = Integer.parseInt(iban.substring(4, 9).replaceAll("[^0-9]", "0"));
        return iid >= 30000 && iid <= 31999;
    }

    /** The Swiss QR Code payload (IG 4.2.2), lines separated by LF, without a trailing separator. */
    public String payload() {
        List<String> lines = new ArrayList<>(List.of("SPC", "0200", "1", iban));
        addAddress(lines, creditor);
        for (int index = 0; index < 7; index++) lines.add("");          // ultimate creditor: must not be filled
        lines.add(amount.toPlainString());
        lines.add(currency);
        if (debtor == null) for (int index = 0; index < 7; index++) lines.add("");
        else addAddress(lines, debtor);
        lines.add("NON");
        lines.add("");                                                   // no reference with NON
        lines.add(message == null ? "" : message);
        lines.add("EPD");
        return String.join("\n", lines);
    }

    private static void addAddress(List<String> lines, Address address) {
        lines.addAll(Arrays.asList("S", address.name(), nullToEmpty(address.street()), nullToEmpty(address.buildingNumber()),
                address.postalCode(), address.town(), address.country()));
    }

    public String iban() { return iban; }
    public Address creditor() { return creditor; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    /** Null if the debtor is not part of the code (blank field on the slip). */
    public Address debtor() { return debtor; }
    public String message() { return message; }

    /** "CH93 0076 2011 6238 5295 7" */
    public String formattedIban() { return iban.replaceAll("(.{4})(?!$)", "$1 "); }

    /** "1 590.00" – blank as thousands separator, two decimals (IG 3.5.3). */
    public String formattedAmount() {
        String plain = amount.toPlainString();
        int dot = plain.indexOf('.');
        String whole = plain.substring(0, dot);
        StringBuilder grouped = new StringBuilder();
        for (int index = 0; index < whole.length(); index++) {
            if (index > 0 && (whole.length() - index) % 3 == 0) grouped.append(' ');
            grouped.append(whole.charAt(index));
        }
        return grouped + plain.substring(dot);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static int length(String value) { return value == null ? 0 : value.length(); }
    private static String join(String separator, String... values) {
        return String.join(separator, Arrays.stream(values).filter(Objects::nonNull).toList());
    }
}
