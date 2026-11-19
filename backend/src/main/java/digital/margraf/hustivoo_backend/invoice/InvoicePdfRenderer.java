package digital.margraf.hustivoo_backend.invoice;

import digital.margraf.hustivoo_backend.business.SenderProfile;
import digital.margraf.hustivoo_backend.document.BusinessDocument;
import digital.margraf.hustivoo_backend.document.BusinessDocumentRenderer;
import digital.margraf.hustivoo_backend.document.BusinessDocumentRenderer.DocumentFormat;
import digital.margraf.hustivoo_backend.document.qrbill.QrBillSlip;
import digital.margraf.hustivoo_backend.document.qrbill.SwissQrBill;
import digital.margraf.hustivoo_backend.quote.QuotePdfRenderer;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A4 invoice in the same design as quotes, with a Swiss QR payment part on the last page whenever the
 * stored data allow a valid one ({@link #qrBill}). Drafts carry the notification variant
 * ("NICHT ZUR ZAHLUNG VERWENDEN", amount 0.00, IG 4.4); cancelled invoices carry no payment part.
 */
@Component
public class InvoicePdfRenderer {
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * @param sender    current settings (drafts) or the snapshot frozen at issue
     * @param quoteNumber number of the quote this invoice was created from, or null
     */
    public byte[] render(Invoice invoice, SenderProfile sender, byte[] logo, String quoteNumber) {
        SwissQrBill.Result qr = qrBill(invoice, sender);
        return BusinessDocumentRenderer.render(document(invoice, quoteNumber, qr.valid() ? new QrBillSlip(qr.bill()) : null),
                sender.document(logo, invoice.getCustomerCountry()), sender.theme(), sender.country());
    }

    /** The QR-bill this invoice would print, or the reasons why it cannot carry one. */
    public static SwissQrBill.Result qrBill(Invoice invoice, SenderProfile sender) {
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            return new SwissQrBill.Result(null, List.of("Stornierte Rechnungen enthalten keinen Zahlteil."));
        }
        boolean draft = invoice.getStatus() == InvoiceStatus.DRAFT;
        SwissQrBill.Address creditor = SwissQrBill.Address.of(sender.name(), sender.street(), sender.postalCode(),
                sender.city(), sender.country());
        SwissQrBill.Address debtor = SwissQrBill.Address.of(invoice.getCustomerName(), invoice.getCustomerStreet(),
                invoice.getCustomerPostalCode(), invoice.getCustomerCity(), invoice.getCustomerCountry());
        BigDecimal amount = draft ? BigDecimal.ZERO.setScale(2) : invoice.getGrandTotal();
        String message = draft ? SwissQrBill.NOT_FOR_PAYMENT : "Rechnung " + invoice.getInvoiceNumber();
        return SwissQrBill.create(sender.iban(), creditor, amount, invoice.getCurrency(), debtor, message);
    }

    static BusinessDocument document(Invoice invoice, String quoteNumber, BusinessDocument.Slip slip) {
        var recipient = new BusinessDocument.Recipient(invoice.getCustomerName(), invoice.getCustomerCompanyName(),
                invoice.getCustomerContactName(), invoice.getCustomerStreet(), invoice.getCustomerPostalCode(),
                invoice.getCustomerCity(), invoice.getCustomerCountry(), invoice.getCustomerNumber());
        List<BusinessDocument.Detail> details = new ArrayList<>(List.of(
                new BusinessDocument.Detail("Rechnungsnummer", invoice.getInvoiceNumber()),
                new BusinessDocument.Detail("Rechnungsdatum", DATE.format(invoice.getIssueDate())),
                new BusinessDocument.Detail("Fällig am", DATE.format(invoice.getDueDate())),
                new BusinessDocument.Detail("Zahlungsfrist", term(invoice.getPaymentTermDays()))));
        if (quoteNumber != null) details.add(new BusinessDocument.Detail("Angebot", quoteNumber));
        details.add(new BusinessDocument.Detail("Währung", invoice.getCurrency()));
        List<BusinessDocument.Item> items = invoice.getItems().stream().map(item -> new BusinessDocument.Item(item.getPosition(),
                item.getDescription(), DocumentFormat.quantity(item.getQuantity()) + " " + QuotePdfRenderer.unit(item.getUnit()),
                item.getUnitPrice(), item.getTaxRate(), item.getLineSubtotal(), item.getLineTax())).toList();
        String stamp = switch (invoice.getStatus()) {
            case DRAFT -> "ENTWURF";
            case CANCELLED -> "STORNIERT";
            default -> null;
        };
        String note = (invoice.getPaymentTermDays() == 0 ? "Zahlbar sofort"
                : "Zahlbar bis " + DATE.format(invoice.getDueDate())) + " · Beträge in " + invoice.getCurrency();
        return new BusinessDocument(invoice.getId(), "RECHNUNG", "Rechnung", invoice.getInvoiceNumber(), stamp, recipient,
                "RECHNUNGSDETAILS", details, "Rechnung für " + invoice.getCustomerName(), invoice.getIntroductionText(),
                invoice.getClosingText(), invoice.getCurrency(), items, invoice.getSubtotal(), invoice.getTaxTotal(),
                invoice.getGrandTotal(), note, slip);
    }

    static String term(int days) {
        return days == 0 ? "sofort" : days == 1 ? "1 Tag" : days + " Tage";
    }
}
