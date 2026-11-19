package digital.margraf.hustivoo_backend.expense;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class ExpenseDtos {
    private ExpenseDtos() {}
    public enum Status { UNPAID, PAID, CANCELLED, OVERDUE }
    public enum InputTax { UNCHECKED, DEDUCTIBLE, NOT_DEDUCTIBLE, PARTIAL }
    public enum Method { BANK_TRANSFER, QR_BILL, CASH, CARD, TWINT, OTHER }
    public record SupplierInput(@NotBlank @Size(max=255) String name, @Size(max=255) String contactPerson,
        @Email @Size(max=320) String email, @Size(max=50) String phone, @Size(max=255) String street,
        @Size(max=30) String houseNumber, @Size(max=20) String postalCode, @Size(max=120) String city,
        @Pattern(regexp="[A-Z]{2}") String country, @Size(max=50) String vatNumber,
        @Pattern(regexp="[A-Z]{2}[0-9A-Z]{13,32}|^$") String iban, @Size(max=4000) String notes) {
        public SupplierInput { name=clean(name); country=country==null?"CH":country.strip().toUpperCase(Locale.ROOT); iban=iban==null?null:iban.replaceAll("\\s", "").toUpperCase(Locale.ROOT); }
    }
    public record CategoryInput(@NotBlank @Size(max=120) String name) { public CategoryInput { name=clean(name); } }
    public record Input(@NotNull UUID supplierId, @NotNull UUID categoryId,
        @NotBlank @Size(max=1000) String description, @Size(max=140) String reference,
        @NotNull LocalDate expenseDate, LocalDate dueDate, @Pattern(regexp="[A-Z]{3}") String currency,
        @NotNull @DecimalMin("0") @Digits(integer=17,fraction=2) BigDecimal netAmount,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal taxRate,
        @DecimalMin("0") @Digits(integer=17,fraction=2) BigDecimal taxAmount,
        @NotNull InputTax inputTax, @Size(max=4000) String notes) {
        public Input { description=clean(description); currency=currency==null?"CHF":currency.strip().toUpperCase(Locale.ROOT); }
    }
    public record PaymentInput(@NotNull @PastOrPresent LocalDate paymentDate, @NotNull Method method) {}
    public record Reason(@NotBlank @Size(max=500) String reason) { public Reason { reason=clean(reason); } }
    static String clean(String s) { return s==null?null:s.strip(); }
}
