package digital.margraf.hustivoo_backend.quote;

import java.util.Map;

/**
 * Field errors detected against tenant data (unknown or foreign customer/service IDs, currency
 * mismatches, amount limits). Unknown and foreign IDs produce the same message (HTTP 400).
 */
public class QuoteValidationException extends RuntimeException {
    private final Map<String, String> fields;
    public QuoteValidationException(Map<String, String> fields) {
        super("Invalid request", null, false, false);
        this.fields = Map.copyOf(fields);
    }
    public Map<String, String> getFields() { return fields; }
}
