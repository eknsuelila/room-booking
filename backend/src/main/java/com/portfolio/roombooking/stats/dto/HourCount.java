package com.portfolio.roombooking.stats.dto;

/**
 * Number of confirmed bookings this week that occupy (any part of) the hour starting at {@code hour}.
 * E.g. hour = 9 counts bookings overlapping [09:00, 10:00).
 */
public record HourCount(int hour, long count) {
}
