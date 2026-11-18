package digital.margraf.hustivoo_backend.quote;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** JSON errors without submitted values or internal details; also used for the PDF endpoint. */
@RestControllerAdvice(assignableTypes = QuoteController.class)
public class QuoteExceptionHandler {
    public record ErrorResponse(String error, Map<String, String> fields) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.put(switch (error.getField()) {
            case "validityRange" -> "validUntil";
            case "currencySupported" -> "currency";
            default -> error.getField();
        }, "Bitte prüfe dieses Feld."));
        return json(400, new ErrorResponse("Invalid request", fields));
    }

    @ExceptionHandler(QuoteValidationException.class)
    ResponseEntity<ErrorResponse> references(QuoteValidationException exception) {
        return json(400, new ErrorResponse("Invalid request", exception.getFields()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class})
    ResponseEntity<ErrorResponse> invalid() { return error(400, "Invalid request"); }

    @ExceptionHandler(QuoteConflictException.class)
    ResponseEntity<ErrorResponse> conflict(QuoteConflictException exception) { return error(409, exception.getMessage()); }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        return error(status, status == 404 ? "Quote not found" : "Authentication required");
    }

    // Reached only if a concurrent change slips past the service checks and hits the V6 guards.
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> integrity() { return error(409, "Quote changed concurrently"); }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> database() { return error(500, "Unable to complete request"); }

    private static ResponseEntity<ErrorResponse> error(int status, String message) {
        return json(status, new ErrorResponse(message, Map.of()));
    }

    private static ResponseEntity<ErrorResponse> json(int status, ErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
