package digital.margraf.hustivoo_backend.servicecatalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

public record ServiceRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 4000) String description,
        @Size(max = 120) String category,
        @NotNull ServiceUnit unit,
        @NotNull @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal unitPrice,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal taxRate) {
    public ServiceRequest {
        name = clean(name); description = clean(description); category = clean(category);
        currency = currency == null ? "CHF" : currency.strip().toUpperCase(Locale.ROOT);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    @AssertTrue(message = "Bitte wähle eine gültige Währung.")
    public boolean isCurrencySupported() {
        if (currency == null) return false;
        try { Currency.getInstance(currency); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }
}
