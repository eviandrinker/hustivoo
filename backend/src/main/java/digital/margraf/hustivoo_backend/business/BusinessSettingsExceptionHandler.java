package digital.margraf.hustivoo_backend.business;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
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

/** Consistent JSON errors for the settings API; never echoes submitted values. */
@RestControllerAdvice(assignableTypes = BusinessSettingsController.class)
public class BusinessSettingsExceptionHandler {
    public record ErrorResponse(String error, Map<String, String> fields) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        // @AssertTrue checks are named "<field>Valid"; report them under the field itself.
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField().replaceFirst("Valid$", ""), "Bitte prüfe dieses Feld."));
        return json(400, new ErrorResponse("Invalid request", fields));
    }

    @ExceptionHandler(LogoImages.InvalidLogoException.class)
    ResponseEntity<ErrorResponse> logo(LogoImages.InvalidLogoException exception) {
        String reason = exception.getMessage().contains("large") ? "Das Logo darf höchstens 2 MB gross sein."
                : exception.getMessage().contains("type") ? "Bitte lade eine PNG- oder JPEG-Datei hoch."
                : exception.getMessage().contains("dimensions") ? "Das Logo muss mindestens 16 × 16 Pixel und höchstens 40 Megapixel haben."
                : "Die Bilddatei konnte nicht gelesen werden.";
        return json(400, new ErrorResponse("Invalid logo", Map.of("logo", reason)));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> mediaType() {
        return error(415, "Unsupported media type");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class, ConstraintViolationException.class})
    ResponseEntity<ErrorResponse> invalid() { return error(400, "Invalid request"); }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        return error(status, switch (status) {
            case 404 -> "Business not found";
            case 403 -> "Only owners can change business settings";
            default -> "Authentication required";
        });
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> database() { return error(500, "Unable to complete request"); }

    private static ResponseEntity<ErrorResponse> error(int status, String message) {
        return json(status, new ErrorResponse(message, Map.of()));
    }

    private static ResponseEntity<ErrorResponse> json(int status, ErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
