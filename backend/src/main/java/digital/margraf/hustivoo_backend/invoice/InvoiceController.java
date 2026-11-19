package digital.margraf.hustivoo_backend.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/businesses/{businessId}")
public class InvoiceController {
    private final InvoiceService invoices;
    public InvoiceController(InvoiceService invoices) { this.invoices = invoices; }

    private static String base(UUID businessId) { return "/api/businesses/" + businessId + "/invoices/"; }

    @GetMapping("/invoices")
    public List<InvoiceResponse.Summary> list(@PathVariable UUID businessId,
            @RequestParam(defaultValue = "") @Size(max = 320) String search,
            @RequestParam(required = false) InvoiceStatus.Display status) {
        return invoices.list(businessId, search, status);
    }

    @PostMapping("/invoices")
    public ResponseEntity<InvoiceResponse> create(@PathVariable UUID businessId, @Valid @RequestBody InvoiceRequest request) {
        InvoiceResponse invoice = invoices.create(businessId, request);
        return ResponseEntity.created(URI.create(base(businessId) + invoice.id())).body(invoice);
    }

    /** Converts an accepted quote; repeated calls return 409 with the existing invoice's ID. */
    @PostMapping("/quotes/{quoteId}/invoice")
    public ResponseEntity<InvoiceResponse> fromQuote(@PathVariable UUID businessId, @PathVariable UUID quoteId) {
        InvoiceResponse invoice = invoices.fromQuote(businessId, quoteId);
        return ResponseEntity.created(URI.create(base(businessId) + invoice.id())).body(invoice);
    }

    @GetMapping("/invoices/{invoiceId}")
    public InvoiceResponse get(@PathVariable UUID businessId, @PathVariable UUID invoiceId) { return invoices.get(businessId, invoiceId); }

    @PutMapping("/invoices/{invoiceId}")
    public InvoiceResponse update(@PathVariable UUID businessId, @PathVariable UUID invoiceId, @Valid @RequestBody InvoiceRequest request) {
        return invoices.update(businessId, invoiceId, request);
    }

    @DeleteMapping("/invoices/{invoiceId}")
    public ResponseEntity<Void> delete(@PathVariable UUID businessId, @PathVariable UUID invoiceId) {
        invoices.delete(businessId, invoiceId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/invoices/{invoiceId}/issue")
    public InvoiceResponse issue(@PathVariable UUID businessId, @PathVariable UUID invoiceId) { return invoices.issue(businessId, invoiceId); }

    @PatchMapping("/invoices/{invoiceId}/cancel")
    public InvoiceResponse cancel(@PathVariable UUID businessId, @PathVariable UUID invoiceId, @Valid @RequestBody InvoiceRequest.Reason reason) {
        return invoices.cancel(businessId, invoiceId, reason);
    }

    @GetMapping("/invoices/{invoiceId}/payments")
    public List<InvoiceResponse.Payment> payments(@PathVariable UUID businessId, @PathVariable UUID invoiceId) {
        return invoices.get(businessId, invoiceId).payments();
    }

    @PostMapping("/invoices/{invoiceId}/payments")
    public ResponseEntity<InvoiceResponse> recordPayment(@PathVariable UUID businessId, @PathVariable UUID invoiceId,
            @Valid @RequestBody InvoiceRequest.Payment payment) {
        return ResponseEntity.status(201).body(invoices.recordPayment(businessId, invoiceId, payment));
    }

    @PatchMapping("/invoices/{invoiceId}/payments/{paymentId}/reverse")
    public InvoiceResponse reversePayment(@PathVariable UUID businessId, @PathVariable UUID invoiceId, @PathVariable UUID paymentId,
            @Valid @RequestBody InvoiceRequest.Reason reason) {
        return invoices.reversePayment(businessId, invoiceId, paymentId, reason);
    }

    @GetMapping("/invoices/{invoiceId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID businessId, @PathVariable UUID invoiceId) {
        InvoiceService.InvoicePdf pdf = invoices.pdf(businessId, invoiceId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.filename(), StandardCharsets.UTF_8).build().toString())
                .body(pdf.content());
    }
}
