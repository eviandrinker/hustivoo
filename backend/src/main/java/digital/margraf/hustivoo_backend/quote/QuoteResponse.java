package digital.margraf.hustivoo_backend.quote;

import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Amounts are decimal strings (scale 2; quantity up to 3 decimals) so JavaScript never loses precision. */
public record QuoteResponse(UUID id, UUID businessId, UUID customerId, String quoteNumber, QuoteStatus status,
        LocalDate issueDate, LocalDate validUntil, String currency, String introductionText, String closingText,
        String notes, String subtotal, String taxTotal, String grandTotal, CustomerSnapshot customer,
        List<Item> items, List<TaxGroup> taxGroups, Instant sentAt, Instant decidedAt,
        Instant createdAt, Instant updatedAt) {

    public record CustomerSnapshot(String customerNumber, String name, String companyName, String contactName,
            String street, String postalCode, String city, String country, String email) {}

    public record Item(UUID id, UUID serviceId, int position, String description, String quantity, ServiceUnit unit,
            String unitPrice, String taxRate, String lineSubtotal, String lineTax, String lineTotal) {}

    public record TaxGroup(String rate, String base, String tax) {}

    /** List row without positions or internal notes. */
    public record Summary(UUID id, UUID businessId, UUID customerId, String quoteNumber, QuoteStatus status,
            String customerName, LocalDate issueDate, LocalDate validUntil, String currency, String grandTotal,
            Instant updatedAt) {
        static Summary from(Quote quote) {
            return new Summary(quote.getId(), quote.getBusinessId(), quote.getCustomerId(), quote.getQuoteNumber(),
                    quote.getStatus(), quote.getCustomerName(), quote.getIssueDate(), quote.getValidUntil(),
                    quote.getCurrency(), money(quote.getGrandTotal()), quote.getUpdatedAt());
        }
    }

    static String money(BigDecimal value) { return value.setScale(2).toPlainString(); }

    static String quantity(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    static QuoteResponse from(Quote quote) {
        List<Item> items = quote.getItems().stream().map(item -> new Item(item.getId(), item.getServiceId(),
                item.getPosition(), item.getDescription(), quantity(item.getQuantity()), item.getUnit(),
                money(item.getUnitPrice()), money(item.getTaxRate()), money(item.getLineSubtotal()),
                money(item.getLineTax()), money(item.getLineTotal()))).toList();
        List<TaxGroup> groups = QuoteCalculator.taxGroups(quote.getItems()).stream()
                .map(group -> new TaxGroup(money(group.rate()), money(group.base()), money(group.tax()))).toList();
        return new QuoteResponse(quote.getId(), quote.getBusinessId(), quote.getCustomerId(), quote.getQuoteNumber(),
                quote.getStatus(), quote.getIssueDate(), quote.getValidUntil(), quote.getCurrency(),
                quote.getIntroductionText(), quote.getClosingText(), quote.getNotes(), money(quote.getSubtotal()),
                money(quote.getTaxTotal()), money(quote.getGrandTotal()),
                new CustomerSnapshot(quote.getCustomerNumber(), quote.getCustomerName(), quote.getCustomerCompanyName(),
                        quote.getCustomerContactName(), quote.getCustomerStreet(), quote.getCustomerPostalCode(),
                        quote.getCustomerCity(), quote.getCustomerCountry(), quote.getCustomerEmail()),
                items, groups, quote.getSentAt(), quote.getDecidedAt(), quote.getCreatedAt(), quote.getUpdatedAt());
    }
}
