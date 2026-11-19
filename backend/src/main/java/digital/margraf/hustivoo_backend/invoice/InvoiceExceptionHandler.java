package digital.margraf.hustivoo_backend.invoice;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** JSON errors without submitted values or internal details; also used for the PDF endpoint. */
@RestControllerAdvice(assignableTypes = InvoiceController.class)
public class InvoiceExceptionHandler {
    /** {@code invoiceId} is only set for "Quote already invoiced", so the client can open that invoice. */
    public record ErrorResponse(String error, Map<String, String> fields, UUID invoiceId) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.put(
                error.getField().equals("currencySupported") ? "currency" : error.getField(), "Bitte prüfe dieses Feld."));
        return json(400, new ErrorResponse("Invalid request", fields, null));
    }

    @ExceptionHandler(InvoiceValidationException.class)
    ResponseEntity<ErrorResponse> references(InvoiceValidationException exception) {
        return json(400, new ErrorResponse("Invalid request", exception.getFields(), null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class})
    ResponseEntity<ErrorResponse> invalid() { return error(400, "Invalid request"); }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> mediaType() { return error(415, "Unsupported media type"); }

    @ExceptionHandler(InvoiceConflictException.class)
    ResponseEntity<ErrorResponse> conflict(InvoiceConflictException exception) {
        return json(409, new ErrorResponse(exception.getMessage(), Map.of(), exception.getInvoiceId()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        return error(status, status == 404 ? "Invoice not found" : "Authentication required");
    }

    // Reached only if a concurrent change slips past the service checks and hits the V8 guards/indexes.
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ErrorResponse> integrity() { return error(409, "Invoice changed concurrently"); }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> database() { return error(500, "Unable to complete request"); }

    private static ResponseEntity<ErrorResponse> error(int status, String message) {
        return json(status, new ErrorResponse(message, Map.of(), null));
    }

    private static ResponseEntity<ErrorResponse> json(int status, ErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
