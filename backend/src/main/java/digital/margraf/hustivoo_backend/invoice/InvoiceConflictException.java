package digital.margraf.hustivoo_backend.invoice;

/** A request that conflicts with the invoice's current state (409). The message is safe to return. */
public class InvoiceConflictException extends RuntimeException {
    private final java.util.UUID invoiceId;

    public InvoiceConflictException(String message) { this(message, null); }

    public InvoiceConflictException(String message, java.util.UUID invoiceId) {
        super(message);
        this.invoiceId = invoiceId;
    }

    /** For "already converted": the existing invoice the client can open instead. */
    public java.util.UUID getInvoiceId() { return invoiceId; }
}
