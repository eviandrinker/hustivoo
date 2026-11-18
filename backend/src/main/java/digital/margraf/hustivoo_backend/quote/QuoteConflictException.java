package digital.margraf.hustivoo_backend.quote;

/** A request that is valid on its own but not in the quote's current status (HTTP 409). */
public class QuoteConflictException extends RuntimeException {
    public QuoteConflictException(String message) { super(message, null, false, false); }
}
