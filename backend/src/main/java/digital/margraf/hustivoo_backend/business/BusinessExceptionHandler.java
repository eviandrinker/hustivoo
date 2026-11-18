package digital.margraf.hustivoo_backend.business;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = BusinessController.class)
public class BusinessExceptionHandler {
    public record ErrorResponse(String error) {}

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorResponse> invalid() { return error(400, "Invalid request"); }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        return error(status, status == 404 ? "Business not found" : "Authentication required");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> persistence() { return error(500, "Unable to complete request"); }

    private static ResponseEntity<ErrorResponse> error(int status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }
}
