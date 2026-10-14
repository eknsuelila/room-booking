package com.portfolio.roombooking.stats;

import com.portfolio.roombooking.booking.Booking;
import com.portfolio.roombooking.booking.BookingRepository;
import com.portfolio.roombooking.booking.BookingStatus;
import com.portfolio.roombooking.booking.BookingTimeRules;
import com.portfolio.roombooking.room.Room;
import com.portfolio.roombooking.room.RoomRepository;
import com.portfolio.roombooking.stats.dto.HourCount;
import com.portfolio.roombooking.stats.dto.RoomUtilization;
import com.portfolio.roombooking.stats.dto.StatsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the admin dashboard numbers for the CURRENT week (Monday-Sunday containing today).
 *
 * <p><b>Design choice:</b> we load this week's bookings once (one SQL query) and aggregate in Java
 * rather than writing SQL with {@code EXTRACT(HOUR ...)}/{@code DATEDIFF}. Date functions differ between
 * H2 and PostgreSQL, the data volume (one week of one office) is small, and plain Java is easy to unit
 * test. For a large multi-site deployment you would push this into SQL or a reporting table.
 *
 * <p><b>Utilization definition:</b> booked minutes / bookable minutes. Bookable minutes = 7 days x the
 * 12 business hours (08:00-20:00) = 5,040 min per room per week. Weekends count as bookable because the
 * system allows weekend bookings; restricting to weekdays would be a one-line change here.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    static final long BOOKABLE_MINUTES_PER_WEEK = 7L
            * Duration.between(BookingTimeRules.OPENING_TIME, BookingTimeRules.CLOSING_TIME).toMinutes();

    private final BookingRepository bookings;
    private final RoomRepository rooms;
    private final Clock clock;

    public StatsService(BookingRepository bookings, RoomRepository rooms, Clock clock) {
        this.bookings = bookings;
        this.rooms = rooms;
        this.clock = clock;
    }

    public StatsResponse stats() {
        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate nextWeekStart = weekStart.plusWeeks(1);

        List<Booking> week = bookings.findStartingBetween(weekStart.atStartOfDay(), nextWeekStart.atStartOfDay());
        List<Booking> confirmed = week.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).toList();

        long bookingsToday = confirmed.stream().filter(b -> b.getStartTime().toLocalDate().equals(today)).count();
        long cancelled = week.size() - confirmed.size();

        // ---- utilization per room: sum booked minutes, divide by the bookable minutes ----
        Map<Long, Long> minutesByRoom = new HashMap<>();
        for (Booking b : confirmed) {
            long minutes = Duration.between(b.getStartTime(), b.getEndTime()).toMinutes();
            minutesByRoom.merge(b.getRoom().getId(), minutes, Long::sum);
        }
        List<RoomUtilization> utilization = new ArrayList<>();
        for (Room room : rooms.findAll()) {
            long minutes = minutesByRoom.getOrDefault(room.getId(), 0L);
            // Show active rooms always, and inactive ones only if they still had bookings this week.
            if (room.isActive() || minutes > 0) {
                utilization.add(new RoomUtilization(room.getId(), room.getName(), room.isActive(), minutes,
                        percent(minutes, BOOKABLE_MINUTES_PER_WEEK)));
            }
        }
        utilization.sort(Comparator.comparingDouble(RoomUtilization::utilizationPercent).reversed()
                .thenComparing(RoomUtilization::roomName));

        List<RoomUtilization> activeOnes = utilization.stream().filter(RoomUtilization::active).toList();
        double average = activeOnes.isEmpty() ? 0 : round1(activeOnes.stream()
                .mapToDouble(RoomUtilization::utilizationPercent).average().orElse(0));

        return new StatsResponse(weekStart, nextWeekStart.minusDays(1), bookingsToday, confirmed.size(), cancelled,
                activeOnes.size(), average, utilization, busiestHours(confirmed));
    }

    /**
     * For each business hour H (08..19) count the bookings overlapping [H:00, H+1:00), using the same
     * half-open overlap test as the booking rules: {@code start < hourEnd && end > hourStart}.
     * A 09:30-11:00 meeting therefore counts for 09, 10 (but not 11).
     */
    static List<HourCount> busiestHours(List<Booking> confirmed) {
        List<HourCount> result = new ArrayList<>();
        for (int hour = BookingTimeRules.OPENING_TIME.getHour(); hour < BookingTimeRules.CLOSING_TIME.getHour(); hour++) {
            LocalTime hourStart = LocalTime.of(hour, 0);
            LocalTime hourEnd = hourStart.plusHours(1);
            long count = confirmed.stream()
                    .filter(b -> b.getStartTime().toLocalTime().isBefore(hourEnd)
                            && b.getEndTime().toLocalTime().isAfter(hourStart))
                    .count();
            result.add(new HourCount(hour, count));
        }
        return result;
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0 : round1(part * 100.0 / whole);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
