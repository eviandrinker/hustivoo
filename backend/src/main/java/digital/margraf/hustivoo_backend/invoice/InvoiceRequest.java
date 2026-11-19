package digital.margraf.hustivoo_backend.invoice;

import digital.margraf.hustivoo_backend.quote.QuoteCalculator;
import digital.margraf.hustivoo_backend.quote.QuoteRequest;
import digital.margraf.hustivoo_backend.quote.QuoteRequest.Item;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Writable invoice fields. Totals, numbers, status, due date, paid amounts, snapshots and business/user
 * IDs are never accepted from the client; unknown JSON properties are ignored and recalculated.
 * Positions use the same shape and rules as quote positions.
 */
public record InvoiceRequest(
        @NotNull UUID customerId,
        @NotNull LocalDate issueDate,
        @NotNull @Min(0) @Max(365) Integer paymentTermDays,
        @Pattern(regexp = "[A-Z]{3}") String currency,
        @Size(max = 4000) String introductionText,
        @Size(max = 4000) String closingText,
        @Size(max = 4000) String notes,
        @NotNull @Size(max = QuoteRequest.MAX_ITEMS) List<@NotNull @Valid Item> items) {

    public InvoiceRequest {
        currency = currency == null || currency.isBlank() ? null : currency.strip().toUpperCase(Locale.ROOT);
        introductionText = clean(introductionText);
        closingText = clean(closingText);
        notes = clean(notes);
    }

    @AssertTrue
    public boolean isCurrencySupported() { return currency == null || QuoteCalculator.supported(currency); }

    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }

    /** A manually recorded incoming payment. */
    public record Payment(
            @NotNull @PastOrPresent LocalDate paymentDate,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) java.math.BigDecimal amount,
            @NotNull PaymentMethod method,
            @Size(max = 140) @jakarta.validation.constraints.Pattern(regexp = "[^\\p{Cntrl}]*") String reference,
            @Size(max = 1000) String note) {
        public Payment {
            reference = clean(reference);
            note = clean(note);
        }
    }

    /** Reason for a cancellation or a payment reversal; always required for traceability. */
    public record Reason(@NotBlank @Size(max = 500) String reason) {
        public Reason { reason = reason == null ? null : reason.strip(); }
    }
}
