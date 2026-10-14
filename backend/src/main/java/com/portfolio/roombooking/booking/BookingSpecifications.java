package com.portfolio.roombooking.booking;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Optional filters for the admin "All bookings" list ({@code GET /api/bookings}).
 * Each method returns {@code null} ("no condition") when its filter is not set, so they can be
 * chained freely with {@code .and(...)}.
 */
final class BookingSpecifications {

    private BookingSpecifications() {
    }

    /** Bookings that START on the given day: start in [day 00:00, next day 00:00). */
    static Specification<Booking> onDate(LocalDate date) {
        return (root, query, cb) -> date == null ? null : cb.and(
                cb.greaterThanOrEqualTo(root.get("startTime"), date.atStartOfDay()),
                cb.lessThan(root.get("startTime"), date.plusDays(1).atStartOfDay()));
    }

    static Specification<Booking> inRoom(Long roomId) {
        return (root, query, cb) -> roomId == null ? null : cb.equal(root.get("room").get("id"), roomId);
    }

    static Specification<Booking> hasStatus(BookingStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}
