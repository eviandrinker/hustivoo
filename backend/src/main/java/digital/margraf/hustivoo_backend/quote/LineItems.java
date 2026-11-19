package digital.margraf.hustivoo_backend.quote;

import digital.margraf.hustivoo_backend.servicecatalog.CatalogService;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceRepository;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import java.math.BigDecimal;
import java.util.*;

/**
 * Turns requested positions into priced snapshots for quotes and invoices, so both documents use the
 * same catalogue rules and the same {@link QuoteCalculator} arithmetic.
 *
 * <p>With a {@code serviceId}, omitted description/unit/unitPrice/taxRate are copied from the catalogue
 * (looked up by ID <em>and</em> business ID); archived services are only accepted if the document
 * already used them. Errors are collected per field ("items[0].unitPrice").
 */
public final class LineItems {
    static final String INVALID = "Bitte prüfe dieses Feld.";

    private LineItems() {}

    public record Prepared(UUID serviceId, String description, BigDecimal quantity, ServiceUnit unit,
            BigDecimal unitPrice, BigDecimal taxRate, QuoteCalculator.Line line) {}

    public static List<Prepared> prepare(ServiceRepository services, UUID businessId, List<QuoteRequest.Item> requested,
            String currency, Set<UUID> retainedServices, Map<String, String> errors) {
        List<Prepared> items = new ArrayList<>();
        for (int index = 0; index < requested.size(); index++) {
            QuoteRequest.Item item = requested.get(index);
            String prefix = "items[" + index + "].";
            CatalogService service = null;
            if (item.serviceId() != null) {
                service = services.findByIdAndBusinessId(item.serviceId(), businessId)
                        .filter(found -> found.getActive() || retainedServices.contains(found.getId())).orElse(null);
                if (service == null) { errors.put(prefix + "serviceId", INVALID); continue; }
                // A catalogue price is only meaningful in its own currency.
                if (item.unitPrice() == null && !service.getCurrency().equals(currency)) errors.put(prefix + "unitPrice", INVALID);
            }
            String description = item.description() != null ? item.description() : service == null ? null : catalogDescription(service);
            ServiceUnit unit = item.unit() != null ? item.unit() : service == null ? null : service.getUnit();
            BigDecimal unitPrice = item.unitPrice() != null ? item.unitPrice() : service == null ? null : service.getUnitPrice();
            BigDecimal taxRate = item.taxRate() != null ? item.taxRate()
                    : service == null ? null : Objects.requireNonNullElse(service.getTaxRate(), BigDecimal.ZERO);
            if (description == null) errors.put(prefix + "description", INVALID);
            if (unit == null) errors.put(prefix + "unit", INVALID);
            if (unitPrice == null) errors.put(prefix + "unitPrice", INVALID);
            if (taxRate == null) errors.put(prefix + "taxRate", INVALID);
            if (description == null || unit == null || unitPrice == null || taxRate == null || errors.containsKey("currency")) continue;
            if (unitPrice.stripTrailingZeros().scale() > QuoteCalculator.minorDigits(currency)) { errors.put(prefix + "unitPrice", INVALID); continue; }
            QuoteCalculator.Line line = QuoteCalculator.line(item.quantity(), unitPrice, taxRate, currency);
            if (!QuoteCalculator.withinLimit(line.total())) { errors.put(prefix + "unitPrice", INVALID); continue; }
            items.add(new Prepared(service == null ? null : service.getId(), description, item.quantity(), unit, unitPrice, taxRate, line));
        }
        return items;
    }

    static String catalogDescription(CatalogService service) {
        String text = service.getDescription() == null ? service.getName() : service.getName() + "\n" + service.getDescription();
        return text.length() > 4000 ? text.substring(0, 4000).strip() : text;
    }
}
