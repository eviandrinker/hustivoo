package digital.margraf.hustivoo_backend.expense;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataAccessException;
import jakarta.validation.ConstraintViolationException;
import java.util.*;

@RestControllerAdvice(assignableTypes = ExpenseController.class)
public class ExpenseExceptionHandler {
    public record ErrorResponse(String error, Map<String, String> fields) {}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.put(
                error.getField().equals("countrySupported") ? "country" : error.getField(), "Bitte prüfe dieses Feld."));
        return ResponseEntity.badRequest().body(new ErrorResponse("Invalid request", fields));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class, org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<ErrorResponse> invalid() { return error(400, "Invalid request"); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        return error(status, status == 404 ? "Resource not found" : status == 409 ? "Operation not allowed" : status == 413 ? "File too large" : "Request failed");
    }
    @ExceptionHandler(ExpenseValidationException.class)
    ResponseEntity<ErrorResponse> fields(ExpenseValidationException exception) { return ResponseEntity.badRequest().body(new ErrorResponse("Invalid request",exception.fields)); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> tooLarge() { return error(413,"File too large"); }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> conflict() { return error(409,"Operation not allowed"); }
    @ExceptionHandler(java.io.IOException.class)
    ResponseEntity<ErrorResponse> storage() { return error(503,"Receipt storage unavailable"); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> database() { return error(500, "Unable to complete request"); }
    private static ResponseEntity<ErrorResponse> error(int status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message, Map.of()));
    }
}
