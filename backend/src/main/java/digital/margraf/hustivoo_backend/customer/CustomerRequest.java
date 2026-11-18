package digital.margraf.hustivoo_backend.customer;

import jakarta.validation.constraints.*;
import java.util.Locale;
import java.util.Arrays;

public record CustomerRequest(
        @NotNull CustomerType type,
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 255) String companyName,
        @Size(max = 120) String firstName,
        @Size(max = 120) String lastName,
        @Email @Size(max = 320) String email,
        @Size(max = 50) String phone,
        @Size(max = 255) String street,
        @Size(max = 20) String postalCode,
        @Size(max = 120) String city,
        @NotBlank @Size(max = 2) String country,
        @Size(max = 4000) String notes) {
    public CustomerRequest {
        displayName = clean(displayName);
        companyName = clean(companyName);
        firstName = clean(firstName);
        lastName = clean(lastName);
        email = clean(email);
        phone = clean(phone);
        street = clean(street);
        postalCode = clean(postalCode);
        city = clean(city);
        country = country == null ? "CH" : country.strip().toUpperCase(Locale.ROOT);
        notes = clean(notes);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    @AssertTrue(message = "Bitte wähle ein gültiges Land.")
    public boolean isCountrySupported() { return Arrays.asList(Locale.getISOCountries()).contains(country); }
}
