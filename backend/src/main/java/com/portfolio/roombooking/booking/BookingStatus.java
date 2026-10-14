package com.portfolio.roombooking.booking;

/**
 * Lifecycle of a booking.
 *
 * <p>Bookings are never deleted: cancelling flips the status to {@link #CANCELLED}. That keeps an
 * audit trail ("who cancelled the all-hands?") and lets statistics count cancellations.
 * Only {@link #CONFIRMED} bookings block a time slot - every overlap query filters on it.
 */
public enum BookingStatus {
    CONFIRMED,
    CANCELLED
}
