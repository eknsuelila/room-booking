package com.portfolio.roombooking.booking.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/**
 * Body of {@code POST /api/bookings} (create) and {@code PUT /api/bookings/{id}} (reschedule).
 *
 * <p>Example:
 * <pre>
 * { "roomId": 3, "title": "Sprint planning", "description": null,
 *   "startTime": "2026-10-15T10:00:00", "endTime": "2026-10-15T11:00:00", "attendees": 6 }
 * </pre>
 * Times are ISO-8601 local date-times WITHOUT an offset - office wall-clock time (see Booking).
 *
 * <p>Only single-field checks live here. Rules involving several fields, the clock or the database
 * (15-minute steps, business hours, capacity, overlaps) are enforced in BookingService/BookingTimeRules.
 */
public record BookingRequest(
        @NotNull Long roomId,
        @NotBlank @Size(max = 150) String title,
        @Size(max = 1000) String description,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull @Min(1) @Max(500) Integer attendees) {
}
