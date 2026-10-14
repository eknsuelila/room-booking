package com.portfolio.roombooking.room.dto;

import java.time.LocalDateTime;

/**
 * One booked block in a room's day schedule ({@code GET /api/rooms/{id}/schedule?date=...}).
 *
 * <p>Privacy rule: everyone can see THAT a slot is taken (otherwise they could not pick a free one),
 * but only the booking's owner and admins see WHAT it is. For everybody else {@code title} is
 * {@code "Booked"} and {@code bookedBy} is {@code null}.
 *
 * @param bookingId lets the owner/admin jump to reschedule; harmless for others
 * @param mine      true when the current user owns this booking (the timeline highlights it)
 */
public record ScheduleEntryResponse(Long bookingId, LocalDateTime startTime, LocalDateTime endTime,
                                    String title, String bookedBy, boolean mine) {
}
