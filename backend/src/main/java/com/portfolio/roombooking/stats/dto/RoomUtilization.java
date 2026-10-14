package com.portfolio.roombooking.stats.dto;

/**
 * How busy one room is this week.
 *
 * @param bookedMinutes      sum of the durations of its confirmed bookings this week
 * @param utilizationPercent bookedMinutes / bookable minutes in the week x 100, one decimal
 */
public record RoomUtilization(Long roomId, String roomName, boolean active, long bookedMinutes,
                              double utilizationPercent) {
}
