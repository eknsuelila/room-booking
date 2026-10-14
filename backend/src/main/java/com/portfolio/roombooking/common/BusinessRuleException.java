package com.portfolio.roombooking.common;

/**
 * Thrown when a request breaks a business rule that the client could have known about up front,
 * e.g. "bookings must start on a 15-minute boundary" or "this room only holds 6 people".
 *
 * <p>Translated to HTTP <b>400 Bad Request</b> by {@link GlobalExceptionHandler}, with the message
 * shown to the user as-is - so messages must be written for humans.
 *
 * <p>Why not use bean validation ({@code @NotNull}, {@code @Min}...) for these? Simple per-field checks
 * do live in the request DTOs. Rules that need several fields at once, the current time, or data
 * from the database (room capacity, active flag) are clearer as plain Java in the service layer.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
