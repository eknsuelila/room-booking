package com.portfolio.roombooking.common;

/**
 * Thrown when a request is valid on its own but clashes with the CURRENT STATE of the data,
 * e.g. the room is already booked for that time, the email is already registered, or a room
 * cannot be deleted because it has bookings.
 *
 * <p>Translated to HTTP <b>409 Conflict</b> by {@link GlobalExceptionHandler}. The distinction from
 * 400 matters to clients: a 400 means "fix your input", a 409 means "your input is fine, but
 * the world changed - pick another slot".
 *
 * <p>It extends {@link RuntimeException} (unchecked) so it does not have to be declared in every
 * method signature, and because Spring rolls back a {@code @Transactional} method automatically
 * when an unchecked exception escapes it.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
