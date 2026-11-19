package digital.margraf.hustivoo_backend.quote;

import digital.margraf.hustivoo_backend.document.BusinessDocument;
import digital.margraf.hustivoo_backend.document.BusinessDocumentRenderer;
import digital.margraf.hustivoo_backend.document.BusinessDocumentRenderer.DocumentFormat;
import digital.margraf.hustivoo_backend.document.DocumentSender;
import digital.margraf.hustivoo_backend.document.DocumentTheme;
import digital.margraf.hustivoo_backend.servicecatalog.ServiceUnit;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A4 quote document. Uses only data stored on the quote (item, customer and sender snapshots and the
 * server-calculated amounts), so issued quotes keep printing exactly what was sent. The layout itself
 * is shared with invoices, see {@link BusinessDocumentRenderer}.
 */
@Component
public class QuotePdfRenderer {
    static final float LEFT = BusinessDocumentRenderer.LEFT;
    static final float RIGHT = BusinessDocumentRenderer.RIGHT;
    static final float CONTENT_WIDTH = BusinessDocumentRenderer.CONTENT_WIDTH;
    static final float TOP = BusinessDocumentRenderer.TOP;
    static final float FOOTER_RULE = BusinessDocumentRenderer.FOOTER_RULE;
    static final float CONTENT_BOTTOM = BusinessDocumentRenderer.CONTENT_BOTTOM;
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * @param sender      sender block (current settings for drafts, the frozen snapshot for issued quotes)
     * @param theme       colours and footer note from the same source
     * @param homeCountry the sender's country; the recipient's country is printed only when it differs
     */
    public byte[] render(Quote quote, DocumentSender sender, DocumentTheme theme, String homeCountry) {
        return BusinessDocumentRenderer.render(document(quote), sender, theme, homeCountry);
    }

    static BusinessDocument document(Quote quote) {
        var recipient = new BusinessDocument.Recipient(quote.getCustomerName(), quote.getCustomerCompanyName(),
                quote.getCustomerContactName(), quote.getCustomerStreet(), quote.getCustomerPostalCode(),
                quote.getCustomerCity(), quote.getCustomerCountry(), quote.getCustomerNumber());
        List<BusinessDocument.Detail> details = List.of(
                new BusinessDocument.Detail("Angebotsnummer", quote.getQuoteNumber()),
                new BusinessDocument.Detail("Datum", DATE.format(quote.getIssueDate())),
                new BusinessDocument.Detail("Gültig bis", DATE.format(quote.getValidUntil())),
                new BusinessDocument.Detail("Währung", quote.getCurrency()));
        List<BusinessDocument.Item> items = quote.getItems().stream().map(item -> new BusinessDocument.Item(item.getPosition(),
                item.getDescription(), quantity(item.getQuantity()) + " " + unit(item.getUnit()), item.getUnitPrice(),
                item.getTaxRate(), item.getLineSubtotal(), item.getLineTax())).toList();
        return new BusinessDocument(quote.getId(), "ANGEBOT", "Angebot", quote.getQuoteNumber(),
                quote.getStatus() == QuoteStatus.DRAFT ? "ENTWURF" : null, recipient, "ANGEBOTSDETAILS", details,
                "Angebot für " + quote.getCustomerName(), quote.getIntroductionText(), quote.getClosingText(), quote.getCurrency(),
                items, quote.getSubtotal(), quote.getTaxTotal(), quote.getGrandTotal(),
                "Gültig bis " + DATE.format(quote.getValidUntil()) + " · Beträge in " + quote.getCurrency(), null);
    }

    static String format(BigDecimal value, int minDigits, int maxDigits) { return DocumentFormat.number(value, minDigits, maxDigits); }

    static String quantity(BigDecimal value) { return DocumentFormat.quantity(value); }

    static String rate(BigDecimal value) { return DocumentFormat.rate(value); }

    /** German document abbreviations; shared with invoices. */
    public static String unit(ServiceUnit unit) {
        return switch (unit) {
            case HOUR -> "Std.";
            case DAY -> "Tag(e)";
            case FIXED -> "pauschal";
            case PIECE -> "Stk.";
            case MONTH -> "Mt.";
        };
    }
}
