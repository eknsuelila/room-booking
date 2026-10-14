package com.portfolio.roombooking.common;

import java.time.Instant;
import java.util.Map;

/**
 * The JSON body of EVERY failed request, so the frontend can handle errors in one place.
 *
 * <p>Example (a 409 overlap):
 * <pre>
 * {
 *   "timestamp": "2026-10-14T09:12:44Z",
 *   "status": 409,
 *   "error": "Conflict",
 *   "message": "Boardroom is already booked from 10:00 to 11:00 on 2026-10-15",
 *   "fieldErrors": {}
 * }
 * </pre>
 * {@code fieldErrors} is only filled for bean-validation failures, e.g. {@code {"title": "must not be blank"}},
 * so a form can show the message next to the right input.
 *
 * <p>A Java {@code record} is an immutable data class: the compiler generates the constructor,
 * getters ({@code status()}), {@code equals}, {@code hashCode} and {@code toString}. Jackson
 * serializes records to JSON out of the box.
 */
public record ApiError(Instant timestamp, int status, String error, String message, Map<String, String> fieldErrors) {

    /** Factory for errors that have no per-field details. */
    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, Map.of());
    }
}
