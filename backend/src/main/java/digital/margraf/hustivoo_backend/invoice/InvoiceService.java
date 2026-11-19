package digital.margraf.hustivoo_backend.invoice;

import digital.margraf.hustivoo_backend.business.BusinessLogo;
import digital.margraf.hustivoo_backend.business.BusinessLogoRepository;
import digital.margraf.hustivoo_backend.business.BusinessResponse;
import digital.margraf.hustivoo_backend.business.BusinessService;
import digital.margraf.hustivoo_backend.business.SenderProfile;
import digital.margraf.hustivoo_backend.customer.Customer;
import digital.margraf.hustivoo_backend.customer.CustomerRepository;
import digital.margraf.hustivoo_backend.document.qrbill.SwissQrBill;
import digital.margraf.hustivoo_backend.quote.LineItems;
import digital.margraf.hustivoo_backend.quote.Quote;
import digital.margraf.hustivoo_backend.quote.QuoteCalculator;
import digital.margraf.hustivoo_backend.quote.QuoteItem;
import digital.margraf.hustivoo_backend.quote.QuoteRepository;
import digital.margraf.hustivoo_backend.quote.QuoteStatus;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

/**
 * Tenant-scoped invoice operations. Every method first resolves the caller's membership through
 * {@link BusinessService} (identity from the Security Context; OWNER and MEMBER have the same rights,
 * as for quotes), then loads invoices, customers, services, quotes and payments exclusively by ID
 * <em>and</em> business ID. State-changing operations lock the invoice (or quote) row, so concurrent
 * requests are serialised; the V8 triggers enforce the same rules in the database.
 */
@Service
@Validated
@Transactional(readOnly = true)
public class InvoiceService {
    static final ZoneId ZONE = ZoneId.of("Europe/Zurich");
    static final int DEFAULT_PAYMENT_TERM = 30;
    private static final String INVALID = "Bitte prüfe dieses Feld.";
    private final InvoiceRepository invoices;
    private final InvoicePaymentRepository payments;
    private final BusinessService businesses;
    private final CustomerRepository customers;
    private final ServiceRepository services;
    private final QuoteRepository quotes;
    private final BusinessLogoRepository logos;
    private final JdbcTemplate jdbc;
    private final InvoicePdfRenderer renderer;

    public InvoiceService(InvoiceRepository invoices, InvoicePaymentRepository payments, BusinessService businesses,
            CustomerRepository customers, ServiceRepository services, QuoteRepository quotes,
            BusinessLogoRepository logos, JdbcTemplate jdbc, InvoicePdfRenderer renderer) {
        this.invoices = invoices; this.payments = payments; this.businesses = businesses; this.customers = customers;
        this.services = services; this.quotes = quotes; this.logos = logos; this.jdbc = jdbc; this.renderer = renderer;
    }

    public record InvoicePdf(String filename, byte[] content) {}

    static LocalDate today() { return LocalDate.now(ZONE); }

    // ------------------------------------------------------------------ reading

    /** {@code filter} is a display status; OVERDUE selects open invoices past their due date. */
    public List<InvoiceResponse.Summary> list(UUID businessId, String search, InvoiceStatus.Display filter) {
        businesses.get(businessId);
        LocalDate today = today();
        boolean overdueOnly = filter == InvoiceStatus.Display.OVERDUE;
        InvoiceStatus status = filter == null || overdueOnly ? null : InvoiceStatus.valueOf(filter.name());
        return invoices.search(businessId, search.strip(), status, overdueOnly, today).stream()
                // "Issued"/"partially paid" lists show only invoices that are not overdue, matching the badges.
                .filter(invoice -> status == null || !status.open() || !invoice.overdue(today))
                .map(invoice -> InvoiceResponse.Summary.from(invoice, today)).toList();
    }

    public InvoiceResponse get(UUID businessId, UUID id) {
        businesses.get(businessId);
        return response(find(businessId, id));
    }

    // ------------------------------------------------------------------ drafts

    @Transactional
    public InvoiceResponse create(UUID businessId, @NotNull @Valid InvoiceRequest request) {
        BusinessResponse business = businesses.get(businessId);
        Prepared prepared = prepare(businessId, business, request, null);
        Invoice invoice = new Invoice(businessId, nextNumber(businessId), null);
        apply(invoice, business, prepared, request);
        return response(invoices.saveAndFlush(invoice));
    }

    @Transactional
    public InvoiceResponse update(UUID businessId, UUID id, @NotNull @Valid InvoiceRequest request) {
        BusinessResponse business = businesses.get(businessId);
        Invoice invoice = lock(businessId, id);
        invoice.requireDraft();
        apply(invoice, business, prepare(businessId, business, request, invoice), request);
        return response(invoices.saveAndFlush(invoice));
    }

    @Transactional
    public void delete(UUID businessId, UUID id) {
        businesses.get(businessId);
        Invoice invoice = lock(businessId, id);
        invoice.requireDraft();
        invoices.delete(invoice);
        invoices.flush();
    }

    /**
     * Creates a draft invoice from an accepted quote: customer, positions (with their stored prices,
     * tax rates and line amounts), currency and texts are copied; the quote itself is not modified.
     * The quote row is locked and {@code uq_invoices_quote} backs the check, so double clicks and
     * parallel requests yield exactly one invoice; later attempts get 409 with the existing invoice ID.
     */
    @Transactional
    public InvoiceResponse fromQuote(UUID businessId, UUID quoteId) {
        BusinessResponse business = businesses.get(businessId);
        Quote quote = quotes.lockScoped(quoteId, businessId).orElseThrow(InvoiceService::missing);
        invoices.findByQuoteIdAndBusinessId(quoteId, businessId).ifPresent(existing -> {
            throw new InvoiceConflictException("Quote already invoiced", existing.getId());
        });
        if (quote.getStatus() != QuoteStatus.ACCEPTED) throw new InvoiceConflictException("Only accepted quotes can be invoiced");
        Customer customer = customers.findByIdAndBusinessId(quote.getCustomerId(), businessId).orElseThrow(InvoiceService::missing);
        Invoice invoice = new Invoice(businessId, nextNumber(businessId), quoteId);
        invoice.editHeader(today(), DEFAULT_PAYMENT_TERM, quote.getCurrency(), quote.getIntroductionText(), quote.getClosingText(), null);
        invoice.snapshot(business.name(), business.country(), customer);
        List<InvoiceItem> items = new ArrayList<>();
        List<QuoteCalculator.Line> lines = new ArrayList<>();
        for (QuoteItem item : quote.getItems()) {
            QuoteCalculator.Line line = new QuoteCalculator.Line(item.getLineSubtotal(), item.getLineTax(), item.getLineTotal());
            lines.add(line);
            items.add(new InvoiceItem(invoice, item.getPosition(), item.getServiceId(), item.getDescription(), item.getQuantity(),
                    item.getUnit(), item.getUnitPrice(), item.getTaxRate(), line));
        }
        invoice.replaceItems(items, QuoteCalculator.totals(lines));
        return response(invoices.saveAndFlush(invoice));
    }

    /** Invoice created from a quote, if any (for the quote detail page). */
    public Optional<InvoiceResponse.QuoteLink> invoiceForQuote(UUID businessId, UUID quoteId) {
        return invoices.findByQuoteIdAndBusinessId(quoteId, businessId)
                .map(invoice -> new InvoiceResponse.QuoteLink(invoice.getId(), invoice.getInvoiceNumber()));
    }

    // ------------------------------------------------------------------ lifecycle

    /** Freezes customer and complete sender data (incl. logo and branding); the content is then immutable. */
    @Transactional
    public InvoiceResponse issue(UUID businessId, UUID id) {
        BusinessResponse business = businesses.get(businessId);
        Invoice invoice = lock(businessId, id);
        invoice.requireDraft();
        Customer customer = customers.findByIdAndBusinessId(invoice.getCustomerId(), businessId).orElseThrow(InvoiceService::missing);
        invoice.snapshot(business.name(), business.country(), customer);
        invoice.issue(businesses.sender(businessId), Instant.now());
        return response(invoices.saveAndFlush(invoice));
    }

    @Transactional
    public InvoiceResponse cancel(UUID businessId, UUID id, @NotNull @Valid InvoiceRequest.Reason reason) {
        businesses.get(businessId);
        Invoice invoice = lock(businessId, id);
        invoice.cancel(reason.reason(), Instant.now());
        return response(invoices.saveAndFlush(invoice));
    }

    // ------------------------------------------------------------------ payments

    @Transactional
    public InvoiceResponse recordPayment(UUID businessId, UUID id, @NotNull @Valid InvoiceRequest.Payment request) {
        UUID user = businesses.access(businessId).userId();
        Invoice invoice = lock(businessId, id);
        if (!invoice.getStatus().open()) throw new InvoiceConflictException("Invoice does not accept payments");
        if (request.amount().stripTrailingZeros().scale() > QuoteCalculator.minorDigits(invoice.getCurrency())
                || request.amount().compareTo(invoice.openAmount()) > 0) {
            throw new InvoiceValidationException(Map.of("amount", INVALID));
        }
        payments.save(new InvoicePayment(invoice, request.paymentDate(), request.amount(), request.method(),
                request.reference(), request.note(), user, Instant.now()));
        invoice.applyPaid(invoice.getPaidTotal().add(request.amount()));
        invoices.saveAndFlush(invoice);
        return response(invoice);
    }

    /** Payments are never edited or deleted: a reversal keeps the original booking, who reversed it and why. */
    @Transactional
    public InvoiceResponse reversePayment(UUID businessId, UUID id, UUID paymentId, @NotNull @Valid InvoiceRequest.Reason reason) {
        UUID user = businesses.access(businessId).userId();
        Invoice invoice = lock(businessId, id);
        InvoicePayment payment = payments.findByIdAndInvoiceIdAndBusinessId(paymentId, id, businessId).orElseThrow(InvoiceService::missing);
        if (!payment.active()) throw new InvoiceConflictException("Payment already reversed");
        payment.reverse(user, reason.reason(), Instant.now());
        payments.save(payment);
        invoice.applyPaid(invoice.getPaidTotal().subtract(payment.getAmount()));
        invoices.saveAndFlush(invoice);
        return response(invoice);
    }

    // ------------------------------------------------------------------ PDF

    /**
     * Drafts print the business's current settings; issued (and cancelled) invoices print only the sender
     * frozen when they were issued, so later changes to settings, logo or customer never alter them.
     */
    public InvoicePdf pdf(UUID businessId, UUID id) {
        SenderProfile current = businesses.sender(businessId);
        Invoice invoice = find(businessId, id);
        SenderProfile sender = invoice.getStatus() == InvoiceStatus.DRAFT ? current : invoice.frozenSender();
        byte[] logo = sender.logoId() == null ? null
                : logos.findByIdAndBusinessId(sender.logoId(), businessId).map(BusinessLogo::getContent).orElse(null);
        String quoteNumber = quoteLink(invoice).map(InvoiceResponse.QuoteLink::quoteNumber).orElse(null);
        return new InvoicePdf("Rechnung-" + invoice.getInvoiceNumber() + ".pdf", renderer.render(invoice, sender, logo, quoteNumber));
    }

    // ------------------------------------------------------------------ helpers

    /** Atomic per-business counter: the row lock serializes concurrent creations until commit. */
    String nextNumber(UUID businessId) {
        Long number = jdbc.queryForObject("""
                INSERT INTO invoice_number_counters (business_id, last_value) VALUES (?, 1)
                ON CONFLICT (business_id) DO UPDATE SET last_value = invoice_number_counters.last_value + 1
                RETURNING last_value
                """, Long.class, businessId);
        return "RE-" + String.format(Locale.ROOT, "%04d", number);
    }

    private InvoiceResponse response(Invoice invoice) {
        List<InvoicePayment> booked = invoice.getId() == null ? List.of()
                : payments.findAllByInvoiceIdAndBusinessIdOrderByPaymentDateAscRecordedAtAsc(invoice.getId(), invoice.getBusinessId());
        SenderProfile sender = invoice.getStatus() == InvoiceStatus.DRAFT ? businesses.sender(invoice.getBusinessId()) : invoice.frozenSender();
        SwissQrBill.Result qr = InvoicePdfRenderer.qrBill(invoice, sender);
        return InvoiceResponse.from(invoice, booked, quoteLink(invoice).orElse(null),
                new InvoiceResponse.QrBill(qr.valid(), qr.problems()), today());
    }

    private Optional<InvoiceResponse.QuoteLink> quoteLink(Invoice invoice) {
        if (invoice.getQuoteId() == null) return Optional.empty();
        return quotes.findByIdAndBusinessId(invoice.getQuoteId(), invoice.getBusinessId())
                .map(quote -> new InvoiceResponse.QuoteLink(quote.getId(), quote.getQuoteNumber()));
    }

    private Invoice find(UUID businessId, UUID id) {
        return invoices.findByIdAndBusinessId(id, businessId).orElseThrow(InvoiceService::missing);
    }

    private Invoice lock(UUID businessId, UUID id) {
        return invoices.lockScoped(id, businessId).orElseThrow(InvoiceService::missing);
    }

    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }

    private record Prepared(Customer customer, String currency, List<LineItems.Prepared> items, QuoteCalculator.Totals totals) {}

    /** Same reference and pricing rules as quotes (see {@link LineItems}). */
    private Prepared prepare(UUID businessId, BusinessResponse business, InvoiceRequest request, Invoice existing) {
        Map<String, String> errors = new LinkedHashMap<>();
        String currency = request.currency() != null ? request.currency()
                : existing != null ? existing.getCurrency() : business.currency();
        if (!QuoteCalculator.supported(currency)) errors.put("currency", INVALID);
        Customer customer = customers.findByIdAndBusinessId(request.customerId(), businessId)
                .filter(found -> !found.getArchived() || existing != null && found.getId().equals(existing.getCustomerId()))
                .orElse(null);
        if (customer == null) errors.put("customerId", INVALID);
        Set<UUID> retained = existing == null ? Set.of() : existing.getItems().stream()
                .map(InvoiceItem::getServiceId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<LineItems.Prepared> items = LineItems.prepare(services, businessId, request.items(), currency, retained, errors);
        QuoteCalculator.Totals totals = QuoteCalculator.totals(items.stream().map(LineItems.Prepared::line).toList());
        if (!QuoteCalculator.withinLimit(totals.grandTotal())) errors.put("items", INVALID);
        if (!errors.isEmpty()) throw new InvoiceValidationException(errors);
        return new Prepared(customer, currency, items, totals);
    }

    private static void apply(Invoice invoice, BusinessResponse business, Prepared prepared, InvoiceRequest request) {
        invoice.editHeader(request.issueDate(), request.paymentTermDays(), prepared.currency(),
                request.introductionText(), request.closingText(), request.notes());
        invoice.snapshot(business.name(), business.country(), prepared.customer());
        List<InvoiceItem> items = new ArrayList<>();
        for (int index = 0; index < prepared.items().size(); index++) {
            LineItems.Prepared item = prepared.items().get(index);
            items.add(new InvoiceItem(invoice, index + 1, item.serviceId(), item.description(), item.quantity(),
                    item.unit(), item.unitPrice(), item.taxRate(), item.line()));
        }
        invoice.replaceItems(items, prepared.totals());
    }
}
