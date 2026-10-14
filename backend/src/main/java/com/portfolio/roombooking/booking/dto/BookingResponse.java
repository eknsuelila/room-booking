package com.portfolio.roombooking.booking.dto;

import com.portfolio.roombooking.booking.Booking;
import com.portfolio.roombooking.booking.BookingStatus;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * How a booking is sent to clients. Includes room and user names (flattened) so the UI does not need
 * extra requests. Must be built inside a transaction (room/user are lazy associations).
 */
public record BookingResponse(Long id, Long roomId, String roomName, String roomLocation,
                              Long userId, String userName, String title, String description,
                              LocalDateTime startTime, LocalDateTime endTime, int attendees,
                              BookingStatus status, Instant createdAt) {

    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), b.getRoom().getId(), b.getRoom().getName(), b.getRoom().getLocation(),
                b.getUser().getId(), b.getUser().getName(), b.getTitle(), b.getDescription(),
                b.getStartTime(), b.getEndTime(), b.getAttendees(), b.getStatus(), b.getCreatedAt());
    }
}
