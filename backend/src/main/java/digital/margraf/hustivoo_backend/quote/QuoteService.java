package digital.margraf.hustivoo_backend.quote;

import digital.margraf.hustivoo_backend.business.BusinessResponse;
import digital.margraf.hustivoo_backend.business.BusinessService;
import digital.margraf.hustivoo_backend.customer.Customer;
import digital.margraf.hustivoo_backend.customer.CustomerRepository;
import digital.margraf.hustivoo_backend.servicecatalog.CatalogService;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceRepository;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tenant-scoped quote operations. Every method first resolves the caller's membership through
 * {@link BusinessService} (identity from the Security Context; OWNER and MEMBER have equal rights),
 * then loads quotes, customers and services exclusively by ID <em>and</em> business ID.
 */
@Service
@Validated
@Transactional(readOnly = true)
public class QuoteService {
    private static final String INVALID = "Bitte prüfe dieses Feld.";
    private final QuoteRepository quotes;
    private final BusinessService businesses;
    private final CustomerRepository customers;
    private final ServiceRepository services;
    private final JdbcTemplate jdbc;
    private final QuotePdfRenderer renderer;

    public QuoteService(QuoteRepository quotes, BusinessService businesses, CustomerRepository customers,
            ServiceRepository services, JdbcTemplate jdbc, QuotePdfRenderer renderer) {
        this.quotes = quotes; this.businesses = businesses; this.customers = customers;
        this.services = services; this.jdbc = jdbc; this.renderer = renderer;
    }

    public record QuotePdf(String filename, byte[] content) {}

    public List<QuoteResponse.Summary> list(UUID businessId, String search, QuoteStatus status) {
        businesses.get(businessId);
        return quotes.search(businessId, search.strip(), status).stream().map(QuoteResponse.Summary::from).toList();
    }

    public QuoteResponse get(UUID businessId, UUID id) {
        businesses.get(businessId);
        return QuoteResponse.from(find(businessId, id));
    }

    @Transactional
    public QuoteResponse create(UUID businessId, @NotNull @Valid QuoteRequest request) {
        BusinessResponse business = businesses.get(businessId);
        Prepared prepared = prepare(businessId, business, request, null);
        Quote quote = new Quote(businessId, nextNumber(businessId));
        apply(quote, business, prepared, request);
        return QuoteResponse.from(quotes.saveAndFlush(quote));
    }

    @Transactional
    public QuoteResponse update(UUID businessId, UUID id, @NotNull @Valid QuoteRequest request) {
        BusinessResponse business = businesses.get(businessId);
        Quote quote = lock(businessId, id);
        quote.requireDraft();
        apply(quote, business, prepare(businessId, business, request, quote), request);
        return QuoteResponse.from(quotes.saveAndFlush(quote));
    }

    @Transactional
    public void delete(UUID businessId, UUID id) {
        businesses.get(businessId);
        Quote quote = lock(businessId, id);
        quote.requireDraft();
        quotes.delete(quote);
        quotes.flush();
    }

    @Transactional
    public QuoteResponse transition(UUID businessId, UUID id, QuoteStatus target) {
        BusinessResponse business = businesses.get(businessId);
        Quote quote = lock(businessId, id);
        if (!quote.getStatus().canTransitionTo(target)) throw new QuoteConflictException("Invalid status transition");
        if (target == QuoteStatus.SENT) {
            if (quote.getItems().isEmpty()) throw new QuoteConflictException("Quote has no items");
            // Final snapshot at issue time; archived customers stay referenceable.
            Customer customer = customers.findByIdAndBusinessId(quote.getCustomerId(), businessId).orElseThrow();
            quote.snapshot(business.name(), customer);
        }
        quote.transitionTo(target, Instant.now());
        return QuoteResponse.from(quotes.saveAndFlush(quote));
    }

    public QuotePdf pdf(UUID businessId, UUID id) {
        BusinessResponse business = businesses.get(businessId);
        Quote quote = find(businessId, id);
        return new QuotePdf("Angebot-" + quote.getQuoteNumber() + ".pdf", renderer.render(quote, business));
    }

    /** Atomic per-business counter: the row lock serializes concurrent creations until commit. */
    String nextNumber(UUID businessId) {
        Long number = jdbc.queryForObject("""
                INSERT INTO quote_number_counters (business_id, last_value) VALUES (?, 1)
                ON CONFLICT (business_id) DO UPDATE SET last_value = quote_number_counters.last_value + 1
                RETURNING last_value
                """, Long.class, businessId);
        return "ANG-" + String.format(Locale.ROOT, "%04d", number);
    }

    private Quote find(UUID businessId, UUID id) {
        return quotes.findByIdAndBusinessId(id, businessId).orElseThrow(QuoteService::missing);
    }

    private Quote lock(UUID businessId, UUID id) {
        return quotes.lockScoped(id, businessId).orElseThrow(QuoteService::missing);
    }

    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }

    private record PreparedItem(UUID serviceId, String description, BigDecimal quantity, ServiceUnit unit,
            BigDecimal unitPrice, BigDecimal taxRate, QuoteCalculator.Line line) {}

    private record Prepared(Customer customer, String currency, List<PreparedItem> items, QuoteCalculator.Totals totals) {}

    /**
     * Resolves references within the business and computes all amounts. Unknown and foreign IDs are
     * indistinguishable. Archived customers/services are only accepted if this draft already used them.
     */
    private Prepared prepare(UUID businessId, BusinessResponse business, QuoteRequest request, Quote existing) {
        Map<String, String> errors = new LinkedHashMap<>();
        String currency = request.currency() != null ? request.currency()
                : existing != null ? existing.getCurrency() : business.currency();
        if (!QuoteCalculator.supported(currency)) errors.put("currency", INVALID);

        Customer customer = customers.findByIdAndBusinessId(request.customerId(), businessId)
                .filter(found -> !found.getArchived() || existing != null && found.getId().equals(existing.getCustomerId()))
                .orElse(null);
        if (customer == null) errors.put("customerId", INVALID);

        Set<UUID> retained = existing == null ? Set.of() : existing.getItems().stream()
                .map(QuoteItem::getServiceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<PreparedItem> items = new ArrayList<>();
        for (int index = 0; index < request.items().size(); index++) {
            QuoteRequest.Item item = request.items().get(index);
            String prefix = "items[" + index + "].";
            CatalogService service = null;
            if (item.serviceId() != null) {
                service = services.findByIdAndBusinessId(item.serviceId(), businessId)
                        .filter(found -> found.getActive() || retained.contains(found.getId())).orElse(null);
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
            items.add(new PreparedItem(service == null ? null : service.getId(), description, item.quantity(), unit, unitPrice, taxRate, line));
        }
        QuoteCalculator.Totals totals = QuoteCalculator.totals(items.stream().map(PreparedItem::line).toList());
        if (!QuoteCalculator.withinLimit(totals.grandTotal())) errors.put("items", INVALID);
        if (!errors.isEmpty()) throw new QuoteValidationException(errors);
        return new Prepared(customer, currency, items, totals);
    }

    private static String catalogDescription(CatalogService service) {
        String text = service.getDescription() == null ? service.getName() : service.getName() + "\n" + service.getDescription();
        return text.length() > 4000 ? text.substring(0, 4000).strip() : text;
    }

    private static void apply(Quote quote, BusinessResponse business, Prepared prepared, QuoteRequest request) {
        quote.editHeader(request.issueDate(), request.validUntil(), prepared.currency(),
                request.introductionText(), request.closingText(), request.notes());
        quote.snapshot(business.name(), prepared.customer());
        List<QuoteItem> items = new ArrayList<>();
        for (int index = 0; index < prepared.items().size(); index++) {
            PreparedItem item = prepared.items().get(index);
            items.add(new QuoteItem(quote, index + 1, item.serviceId(), item.description(), item.quantity(),
                    item.unit(), item.unitPrice(), item.taxRate(), item.line()));
        }
        quote.replaceItems(items, prepared.totals());
    }
}
