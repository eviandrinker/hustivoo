package digital.margraf.hustivoo_backend.business;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.Currency;
import java.util.Locale;

public record CreateBusinessRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 120) String industry,
        @Size(max = 120) String legalForm,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String country,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
    public CreateBusinessRequest {
        name = name == null ? null : name.strip();
        industry = optionalText(industry);
        legalForm = optionalText(legalForm);
        country = country == null ? "CH" : country.strip().toUpperCase(Locale.ROOT);
        currency = currency == null ? "CHF" : currency.strip().toUpperCase(Locale.ROOT);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @AssertTrue(message = "Country must be an ISO 3166-1 alpha-2 code")
    public boolean isCountrySupported() {
        return Arrays.asList(Locale.getISOCountries()).contains(country);
    }

    @AssertTrue(message = "Currency must be an ISO 4217 code")
    public boolean isCurrencySupported() {
        try { Currency.getInstance(currency); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }
}
