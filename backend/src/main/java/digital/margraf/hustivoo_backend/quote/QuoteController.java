package digital.margraf.hustivoo_backend.quote;

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
@RequestMapping("/api/businesses/{businessId}/quotes")
public class QuoteController {
    private final QuoteService quotes;
    public QuoteController(QuoteService quotes) { this.quotes = quotes; }

    @GetMapping
    public List<QuoteResponse.Summary> list(@PathVariable UUID businessId,
            @RequestParam(defaultValue = "") @Size(max = 320) String search,
            @RequestParam(required = false) QuoteStatus status) {
        return quotes.list(businessId, search, status);
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> create(@PathVariable UUID businessId, @Valid @RequestBody QuoteRequest request) {
        QuoteResponse quote = quotes.create(businessId, request);
        return ResponseEntity.created(URI.create("/api/businesses/" + businessId + "/quotes/" + quote.id())).body(quote);
    }

    @GetMapping("/{quoteId}")
    public QuoteResponse get(@PathVariable UUID businessId, @PathVariable UUID quoteId) { return quotes.get(businessId, quoteId); }

    @PutMapping("/{quoteId}")
    public QuoteResponse update(@PathVariable UUID businessId, @PathVariable UUID quoteId, @Valid @RequestBody QuoteRequest request) {
        return quotes.update(businessId, quoteId, request);
    }

    @DeleteMapping("/{quoteId}")
    public ResponseEntity<Void> delete(@PathVariable UUID businessId, @PathVariable UUID quoteId) {
        quotes.delete(businessId, quoteId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{quoteId}/send")
    public QuoteResponse send(@PathVariable UUID businessId, @PathVariable UUID quoteId) {
        return quotes.transition(businessId, quoteId, QuoteStatus.SENT);
    }

    @PatchMapping("/{quoteId}/accept")
    public QuoteResponse accept(@PathVariable UUID businessId, @PathVariable UUID quoteId) {
        return quotes.transition(businessId, quoteId, QuoteStatus.ACCEPTED);
    }

    @PatchMapping("/{quoteId}/reject")
    public QuoteResponse reject(@PathVariable UUID businessId, @PathVariable UUID quoteId) {
        return quotes.transition(businessId, quoteId, QuoteStatus.REJECTED);
    }

    @GetMapping("/{quoteId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID businessId, @PathVariable UUID quoteId) {
        QuoteService.QuotePdf pdf = quotes.pdf(businessId, quoteId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.filename(), StandardCharsets.UTF_8).build().toString())
                .body(pdf.content());
    }
}
