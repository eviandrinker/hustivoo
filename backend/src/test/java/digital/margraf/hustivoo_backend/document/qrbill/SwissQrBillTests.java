package digital.margraf.hustivoo_backend.document.qrbill;

import io.nayuki.qrcodegen.QrCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/** Swiss QR Code data structure and validation (IG QR-bill v2.4, chapters 3–6). */
class SwissQrBillTests {
    static final String IBAN = "CH93 0076 2011 6238 5295 7";
    static final SwissQrBill.Address CREDITOR = SwissQrBill.Address.of("Studio Nord GmbH", "Seestrasse 12", "8002", "Zürich", "CH");
    static final SwissQrBill.Address DEBTOR = SwissQrBill.Address.of("Muster AG", "Bahnhofstrasse 1a", "3000", "Bern", "CH");

    static SwissQrBill valid(BigDecimal amount, SwissQrBill.Address debtor, String message) {
        SwissQrBill.Result result = SwissQrBill.create(IBAN, CREDITOR, amount, "CHF", debtor, message);
        assertThat(result.problems()).isEmpty();
        return result.bill();
    }

    @Test void payloadFollowsTheDataStructureLineByLine() {
        String payload = valid(new BigDecimal("1590.5"), DEBTOR, "Rechnung RE-0001").payload();
        assertThat(payload).isEqualTo(String.join("\n",
                "SPC", "0200", "1", "CH9300762011623852957",
                "S", "Studio Nord GmbH", "Seestrasse", "12", "8002", "Zürich", "CH",
                "", "", "", "", "", "", "",                                   // ultimate creditor: never filled
                "1590.50", "CHF",
                "S", "Muster AG", "Bahnhofstrasse", "1a", "3000", "Bern", "CH",
                "NON", "", "Rechnung RE-0001", "EPD"));
        assertThat(payload.split("\n", -1)).hasSize(31);
        assertThat(payload).doesNotEndWith("\n").doesNotContain("\r");
    }

    @Test void debtorIsOptionalAndOmittedWhenIncompleteOrUnsupported() {
        for (SwissQrBill.Address debtor : new SwissQrBill.Address[] {
                null,
                SwissQrBill.Address.of("Muster AG", "Bahnhofstrasse 1", null, "Bern", "CH"),   // no postal code
                SwissQrBill.Address.of("漢字 GmbH", "Bahnhofstrasse 1", "3000", "Bern", "CH"),  // outside the character set
                SwissQrBill.Address.of("x".repeat(71), null, "3000", "Bern", "CH")}) {          // name too long
            SwissQrBill bill = valid(new BigDecimal("10.00"), debtor, null);
            assertThat(bill.debtor()).isNull();
            String[] lines = bill.payload().split("\n", -1);
            for (int index = 20; index <= 26; index++) assertThat(lines[index]).isEmpty();
            assertThat(lines[29]).isEmpty();      // no message
        }
    }

    @Test void streetNumbersAreSplitWhereRecognisable() {
        assertThat(SwissQrBill.Address.of("A", "Seestrasse 12", "8002", "Zürich", "CH")).extracting("street", "buildingNumber")
                .containsExactly("Seestrasse", "12");
        assertThat(SwissQrBill.Address.of("A", "Rue du Lac 3-5", "1000", "Lausanne", "CH")).extracting("street", "buildingNumber")
                .containsExactly("Rue du Lac", "3-5");
        assertThat(SwissQrBill.Address.of("A", "Postfach", "8002", "Zürich", "CH")).extracting("street", "buildingNumber")
                .containsExactly("Postfach", null);
        assertThat(SwissQrBill.Address.of("A", null, "8002", "Zürich", "CH").streetLine()).isEmpty();
        assertThat(SwissQrBill.Address.of("A", null, "10115", "Berlin", "DE").placeLine()).isEqualTo("DE – 10115 Berlin");
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            ";keine IBAN",
            "CH93 0076 2011 6238 5295 8;ungültig",                      // checksum
            "CH93 0076 2011 6238 5295;ungültig",                          // length
            "DE89 3704 0044 0532 0130 00;CH/LI",                          // foreign IBAN
            "CH44 3199 9123 0008 8901 2;QR-IBAN"})                        // IID 31999 → QR-IBAN needs QRR
    void invalidAccountsAreRejectedWithReasons(String iban, String reason) {
        SwissQrBill.Result result = SwissQrBill.create(iban, CREDITOR, BigDecimal.TEN, "CHF", DEBTOR, null);
        assertThat(result.valid()).isFalse();
        assertThat(String.join(" ", result.problems())).contains(reason);
    }

    @Test void liechtensteinIbanIsAccepted() {
        assertThat(SwissQrBill.create("LI21 0881 0000 2324 013A A", CREDITOR, BigDecimal.TEN, "CHF", null, null).problems()).isEmpty();
    }

    @Test void amountsAndCurrenciesFollowTheLimits() {
        assertThat(valid(new BigDecimal("0.01"), null, null).payload()).contains("\n0.01\nCHF\n");
        assertThat(valid(new BigDecimal("999999999.99"), null, null).formattedAmount()).isEqualTo("999 999 999.99");
        assertThat(valid(new BigDecimal("1590"), null, null).formattedAmount()).isEqualTo("1 590.00");
        assertThat(valid(new BigDecimal("0.10"), null, null).formattedAmount()).isEqualTo("0.10");
        assertThat(SwissQrBill.create(IBAN, CREDITOR, new BigDecimal("1000000000.00"), "CHF", null, null).valid()).isFalse();
        assertThat(SwissQrBill.create(IBAN, CREDITOR, BigDecimal.ZERO, "CHF", null, null).valid()).isFalse();
        assertThat(SwissQrBill.create(IBAN, CREDITOR, new BigDecimal("-5"), "CHF", null, null).valid()).isFalse();
        assertThat(SwissQrBill.create(IBAN, CREDITOR, new BigDecimal("1.005"), "CHF", null, null).valid()).isFalse();
        assertThat(SwissQrBill.create(IBAN, CREDITOR, BigDecimal.TEN, "EUR", null, null).valid()).isTrue();
        assertThat(String.join(" ", SwissQrBill.create(IBAN, CREDITOR, BigDecimal.TEN, "USD", null, null).problems())).contains("CHF oder EUR");
    }

    @Test void draftsUseTheOfficialNotForPaymentVariant() {
        SwissQrBill bill = valid(new BigDecimal("0.00"), DEBTOR, SwissQrBill.NOT_FOR_PAYMENT);
        assertThat(bill.payload()).contains("\n0.00\nCHF\n").contains("\nNICHT ZUR ZAHLUNG VERWENDEN\nEPD");
        // 0.00 is only valid together with the notification text.
        assertThat(SwissQrBill.create(IBAN, CREDITOR, new BigDecimal("0.00"), "CHF", null, "Rechnung").valid()).isFalse();
    }

    @Test void creditorAddressMustBeCompleteAndEncodable() {
        assertThat(String.join(" ", SwissQrBill.create(IBAN, SwissQrBill.Address.of("Studio", "Seestrasse 1", null, null, "CH"),
                BigDecimal.TEN, "CHF", null, null).problems())).contains("Postleitzahl und Ort");
        assertThat(String.join(" ", SwissQrBill.create(IBAN, SwissQrBill.Address.of("Studio 🚀", null, "8002", "Zürich", "CH"),
                BigDecimal.TEN, "CHF", null, null).problems())).contains("nicht zulässig");
        assertThat(String.join(" ", SwissQrBill.create(IBAN, SwissQrBill.Address.of("x".repeat(71), null, "8002", "Zürich", "CH"),
                BigDecimal.TEN, "CHF", null, null).problems())).contains("70 Zeichen");
        assertThat(SwissQrBill.create(IBAN, CREDITOR, BigDecimal.TEN, "CHF", null, "x".repeat(141)).valid()).isFalse();
    }

    @Test void characterSetMatchesTheGuidelines() {
        assertThat(SwissQrBill.supported("Zürich Ærøskøbing Łódź Ștefan Țepeș € àéîõü ß")).isTrue();
        for (String invalid : new String[] {"漢字", "🚀", "Tab\there", "Line\nbreak", "Ǆ"}) assertThat(SwissQrBill.supported(invalid)).as(invalid).isFalse();
    }

    @Test void qrCodeUsesLevelMAndAtMostVersion25() {
        SwissQrBill longest = valid(new BigDecimal("999999999.99"),
                new SwissQrBill.Address("D".repeat(70), "S".repeat(70), "B".repeat(16), "1".repeat(16), "T".repeat(35), "CH"), "M".repeat(140));
        assertThat(longest.debtor()).isNotNull();   // maximum lengths of every element
        QrCode code = QrBillSlip.encode(longest.payload());
        assertThat(code.errorCorrectionLevel).isEqualTo(QrCode.Ecc.MEDIUM);
        assertThat(code.version).isLessThanOrEqualTo(25);
        QrCode small = QrBillSlip.encode(valid(BigDecimal.TEN, null, null).payload());
        assertThat(small.errorCorrectionLevel).isEqualTo(QrCode.Ecc.MEDIUM);
        assertThat(small.version).isLessThan(code.version);   // smallest possible version, then scaled to 46 mm
    }
}
