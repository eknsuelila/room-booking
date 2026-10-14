package com.portfolio.roombooking.stats;

import com.portfolio.roombooking.booking.Booking;
import com.portfolio.roombooking.booking.BookingRepository;
import com.portfolio.roombooking.booking.BookingStatus;
import com.portfolio.roombooking.room.Room;
import com.portfolio.roombooking.room.RoomRepository;
import com.portfolio.roombooking.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * UNIT test for the dashboard maths with a fixed clock (Wednesday 2026-10-14, week = Oct 12-18).
 */
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-14T09:00:00Z"), ZoneOffset.UTC);

    @Mock private BookingRepository bookings;
    @Mock private RoomRepository rooms;

    private static Room room(long id, String name) {
        Room r = new Room(name, "Floor 1", 8);
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private static Booking booking(Room room, String start, String end, BookingStatus status) {
        Booking b = new Booking(room, new User("U", "u@example.com", "x"));
        b.setStartTime(LocalDateTime.parse(start));
        b.setEndTime(LocalDateTime.parse(end));
        b.setStatus(status);
        return b;
    }

    @Test
    void computesTodayWeekUtilizationAndBusiestHours() {
        Room atlas = room(1, "Atlas");
        Room nova = room(2, "Nova");
        when(rooms.findAll()).thenReturn(List.of(atlas, nova));
        when(bookings.findStartingBetween(LocalDate.of(2026, 10, 12).atStartOfDay(), LocalDate.of(2026, 10, 19).atStartOfDay()))
                .thenReturn(List.of(
                        booking(atlas, "2026-10-14T09:30", "2026-10-14T11:00", BookingStatus.CONFIRMED),  // today, 90 min
                        booking(atlas, "2026-10-15T09:00", "2026-10-15T10:00", BookingStatus.CONFIRMED),  // 60 min
                        booking(nova, "2026-10-13T09:00", "2026-10-13T09:30", BookingStatus.CANCELLED)));

        var stats = new StatsService(bookings, rooms, FIXED_CLOCK).stats();

        assertThat(stats.weekStart()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(stats.bookingsToday()).isEqualTo(1);
        assertThat(stats.bookingsThisWeek()).isEqualTo(2);
        assertThat(stats.cancelledThisWeek()).isEqualTo(1);
        // Atlas: 150 of 5040 bookable minutes = 2.976% -> 3.0; Nova 0 (cancelled bookings don't count)
        assertThat(stats.roomUtilization().get(0).roomName()).isEqualTo("Atlas");
        assertThat(stats.roomUtilization().get(0).utilizationPercent()).isEqualTo(3.0);
        assertThat(stats.roomUtilization().get(1).utilizationPercent()).isZero();
        // Hour 9 is touched by both Atlas bookings, hour 10 by one (09:30-11:00), hour 11 by none.
        assertThat(stats.busiestHours()).hasSize(12);
        assertThat(stats.busiestHours().get(1).hour()).isEqualTo(9);
        assertThat(stats.busiestHours().get(1).count()).isEqualTo(2);
        assertThat(stats.busiestHours().get(2).count()).isEqualTo(1);
        assertThat(stats.busiestHours().get(3).count()).isZero();
    }
}
