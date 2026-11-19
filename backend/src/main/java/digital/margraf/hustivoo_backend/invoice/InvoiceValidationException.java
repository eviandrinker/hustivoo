package digital.margraf.hustivoo_backend.invoice;

import java.util.Map;

/** Field errors detected by the service (references, amounts); reported like bean validation errors. */
public class InvoiceValidationException extends RuntimeException {
    private final Map<String, String> fields;

    public InvoiceValidationException(Map<String, String> fields) {
        super("Invalid request");
        this.fields = Map.copyOf(fields);
    }

    public Map<String, String> getFields() { return fields; }
}
