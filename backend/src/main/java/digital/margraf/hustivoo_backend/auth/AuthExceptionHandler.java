package digital.margraf.hustivoo_backend.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {
    public record ErrorResponse(String error) {}

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponse> credentials() { return error(401, "Invalid email or password"); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorResponse> duplicate(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                    && "uq_users_email".equals(constraint.getConstraintName())) {
                return error(409, "Email already registered");
            }
            cause = cause.getCause();
        }
        return error(500, "Unable to complete request");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> invalid() { return error(400, "Invalid request"); }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorResponse> status(ResponseStatusException exception) {
        return error(exception.getStatusCode().value(), "Request could not be completed");
    }

    private static ResponseEntity<ErrorResponse> error(int status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }
}
