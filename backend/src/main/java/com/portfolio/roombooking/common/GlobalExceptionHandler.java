package com.portfolio.roombooking.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions thrown anywhere in a controller/service into consistent {@link ApiError} JSON.
 *
 * <p><b>Where it fits:</b> controllers and services never build error responses themselves - they just
 * {@code throw new ConflictException("...")}. The exception bubbles up out of the controller method,
 * Spring MVC looks for a matching {@code @ExceptionHandler} here, and the method's return value becomes
 * the HTTP response. This keeps the "happy path" code clean.
 *
 * <p>{@code @RestControllerAdvice} = "advice" (cross-cutting logic) that applies to ALL
 * {@code @RestController}s, with return values written as JSON ({@code @ResponseBody}).
 *
 * <p>Status code mapping:
 * <pre>
 *  MethodArgumentNotValidException (@Valid failed) -> 400 + fieldErrors
 *  BusinessRuleException                           -> 400  (e.g. "outside business hours")
 *  malformed JSON / bad query parameter types      -> 400
 *  BadCredentialsException                         -> 401  (wrong email/password)
 *  AccessDeniedException                           -> 403  (@PreAuthorize failed, not your booking)
 *  ResourceNotFoundException                       -> 404
 *  ConflictException / DB unique-constraint error  -> 409  (overlap, duplicate name/email)
 * </pre>
 * Requests WITHOUT a valid token never reach a controller; they get a 401 from the security filter
 * chain (see SecurityConfig).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * {@code @Valid} on a {@code @RequestBody} failed. We collect one message per field so the
     * frontend can display them next to the matching inputs.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();   // keeps the order fields were reported
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fieldErrors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ApiError body = new ApiError(Instant.now(), 400, "Bad Request", "Validation failed", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** A business rule such as "bookings must be in 15-minute steps" was broken. */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Unparseable JSON, a missing required query parameter, or e.g. {@code ?date=yesterday}. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadInput(Exception ex) {
        String message = ex instanceof MissingServletRequestParameterException m
                ? "Missing required parameter '" + m.getParameterName() + "'"
                : ex instanceof MethodArgumentTypeMismatchException t
                ? "Invalid value for parameter '" + t.getName() + "'"
                : "Malformed request";
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Safety net: a database constraint (UNIQUE, CHECK, FOREIGN KEY) rejected a write that our own
     * checks did not catch - typically two admins creating a room with the same name at the same
     * instant. We do not leak the SQL details to the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The change conflicts with existing data");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    /**
     * Thrown by {@code @PreAuthorize} when the role does not match (e.g. a USER calling an admin
     * endpoint), and by our services for ownership checks ("not your booking").
     * Without this handler Spring would still answer 403, but with an empty body.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        String message = ex.getMessage() == null || ex.getMessage().equals("Access Denied")
                ? "You do not have permission to perform this action"
                : ex.getMessage();
        return build(HttpStatus.FORBIDDEN, message);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.getReasonPhrase(), message));
    }
}
