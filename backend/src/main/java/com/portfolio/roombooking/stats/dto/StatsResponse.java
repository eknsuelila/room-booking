package com.portfolio.roombooking.stats.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Response of {@code GET /api/stats} (admin dashboard).
 *
 * @param weekStart          Monday of the current week
 * @param weekEnd            Sunday of the current week
 * @param bookingsToday      confirmed bookings starting today
 * @param bookingsThisWeek   confirmed bookings starting this week (Mon-Sun)
 * @param cancelledThisWeek  cancelled bookings that were scheduled this week
 * @param activeRooms        rooms currently open for booking
 * @param averageUtilization mean utilization of the active rooms, in percent
 * @param roomUtilization    per-room utilization, busiest first
 * @param busiestHours       one entry per business hour (08..19)
 */
public record StatsResponse(LocalDate weekStart, LocalDate weekEnd, long bookingsToday, long bookingsThisWeek,
                            long cancelledThisWeek, long activeRooms, double averageUtilization,
                            List<RoomUtilization> roomUtilization, List<HourCount> busiestHours) {
}
