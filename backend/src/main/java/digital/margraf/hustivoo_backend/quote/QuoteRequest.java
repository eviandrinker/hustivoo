package digital.margraf.hustivoo_backend.quote;

import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Writable quote fields. Totals, numbers, status, snapshots and business/user IDs are never accepted
 * from the client; unknown JSON properties such as {@code grandTotal} are ignored and recalculated.
 */
public record QuoteRequest(
        @NotNull UUID customerId,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate validUntil,
        @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 4000) String introductionText,
        @Size(max = 4000) String closingText,
        @Size(max = 4000) String notes,
        @NotNull @Size(max = 200) List<@NotNull @Valid Item> items) {

    public static final int MAX_ITEMS = 200;

    public QuoteRequest {
        currency = currency == null || currency.isBlank() ? null : currency.strip().toUpperCase(Locale.ROOT);
        introductionText = clean(introductionText);
        closingText = clean(closingText);
        notes = clean(notes);
    }

    /**
     * A position. With {@code serviceId}, omitted description/unit/unitPrice/taxRate are copied from
     * the catalogue as a snapshot; supplied values override them. Free positions need all values.
     */
    public record Item(
            UUID serviceId,
            @Size(max = 4000) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 9, fraction = 3) BigDecimal quantity,
            ServiceUnit unit,
            @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal unitPrice,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal taxRate) {
        public Item { description = clean(description); }
    }

    @AssertTrue
    public boolean isValidityRange() { return issueDate == null || validUntil == null || !validUntil.isBefore(issueDate); }

    @AssertTrue
    public boolean isCurrencySupported() { return currency == null || QuoteCalculator.supported(currency); }

    // Preserve line breaks inside text but drop surrounding whitespace; blank becomes null.
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
