package com.portfolio.roombooking.config;

import com.portfolio.roombooking.booking.Booking;
import com.portfolio.roombooking.booking.BookingRepository;
import com.portfolio.roombooking.booking.BookingStatus;
import com.portfolio.roombooking.room.Room;
import com.portfolio.roombooking.room.RoomRepository;
import com.portfolio.roombooking.user.Role;
import com.portfolio.roombooking.user.User;
import com.portfolio.roombooking.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * Fills an empty database with demo users, 8 rooms and a realistic spread of bookings, so a recruiter
 * can log in and see a lively app immediately.
 *
 * <p>{@code CommandLineRunner}: Spring Boot calls {@link #run} once, after the application context has
 * started (Flyway has already created the tables by then).
 * {@code @ConditionalOnProperty}: the bean only exists when {@code app.seed-demo-data=true}; integration
 * tests set it to false so they start from an empty database.
 *
 * <p>Idempotent: if the admin account already exists (e.g. PostgreSQL in Docker after a restart) it does
 * nothing. Bookings are saved directly through the repository, bypassing BookingService on purpose: some
 * demo bookings are earlier THIS week (in the past), which the service would rightly refuse - but the
 * stats page needs them. The schedule below is hand-written to contain no overlaps.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    public static final String ADMIN_EMAIL = "admin@example.com";
    public static final String USER_EMAIL = "user@example.com";
    public static final String DEMO_PASSWORD = "password123";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataSeeder(UserRepository users, RoomRepository rooms, BookingRepository bookings,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.rooms = rooms;
        this.bookings = bookings;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.existsByEmailIgnoreCase(ADMIN_EMAIL)) {
            return;
        }
        String hash = passwordEncoder.encode(DEMO_PASSWORD);   // same demo password for everyone
        User admin = users.save(new User("Ada Admin", ADMIN_EMAIL, hash, Role.ADMIN));
        User user = users.save(new User("Uma User", USER_EMAIL, hash, Role.USER));
        User alice = users.save(new User("Alice Johnson", "alice@example.com", hash, Role.USER));
        User bob = users.save(new User("Bob Smith", "bob@example.com", hash, Role.USER));

        Room board = room("Boardroom", "Floor 5, North wing", 16, "Executive boardroom with a long table.",
                "Projector", "Video Conference", "Whiteboard", "Phone");
        Room atlas = room("Atlas", "Floor 3, East wing", 10, "Bright room for team meetings.",
                "TV Screen", "Whiteboard", "Video Conference");
        Room nova = room("Nova", "Floor 3, West wing", 6, "Mid-size room, great for workshops.",
                "Whiteboard", "TV Screen");
        Room orbit = room("Orbit", "Floor 2, East wing", 8, "Hybrid meeting room with a ceiling mic.",
                "Video Conference", "TV Screen", "Phone");
        Room focus = room("Focus Pod", "Floor 2, Quiet zone", 2, "Phone-booth style pod for 1:1s and calls.",
                "Phone");
        Room studio = room("Studio", "Floor 1, Lobby", 12, "Flexible space for demos and training.",
                "Projector", "Whiteboard", "TV Screen");
        Room harbor = room("Harbor", "Floor 4, South wing", 4, "Small room with a view.",
                "TV Screen", "Whiteboard");
        Room summit = room("Summit", "Floor 6, Rooftop", 30, "Large event space for all-hands and talks.",
                "Projector", "Video Conference", "Phone", "Whiteboard");
        summit.setActive(false);   // demo of an inactive room (under renovation)
        summit.setDescription("Large event space for all-hands and talks. Closed for renovation.");

        // Bookings spread over this week and next week, at fixed offsets from Monday so every run
        // produces the same, overlap-free schedule.
        LocalDate monday = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate today = LocalDate.now(clock);
        for (int week = 0; week < 2; week++) {
            for (int day = 0; day < 5; day++) {
                LocalDate d = monday.plusWeeks(week).plusDays(day);
                book(board, admin, d, "09:00", "10:00", "Leadership sync", 8);
                book(atlas, alice, d, "09:30", "10:00", "Team stand-up", 7);
                book(nova, bob, d, "13:00", "14:30", "Design review", 5);
                if (day % 2 == 0) {
                    book(orbit, user, d, "11:00", "12:00", "Client call", 4);
                    book(studio, alice, d, "14:00", "16:00", "Onboarding training", 10);
                } else {
                    book(atlas, user, d, "15:00", "16:00", "1:1 planning", 2);
                    book(focus, bob, d, "10:00", "10:30", "Recruiter call", 1);
                }
                if (day == 2) {
                    book(board, bob, d, "16:00", "18:00", "Quarterly business review", 14);
                }
            }
        }
        // A few bookings for today specifically (if today is a weekend they simply land on the weekend).
        book(harbor, user, today, "17:00", "18:00", "Interview debrief", 3);
        book(atlas, admin, today, "18:00", "19:30", "Board prep", 6);
        // A cancelled booking so the status filter and stats have something to show.
        Booking cancelled = book(orbit, alice, today.plusDays(1), "16:00", "17:00", "Vendor demo", 5);
        cancelled.setStatus(BookingStatus.CANCELLED);

        log.info("Seeded demo data. Logins: {} / {} (admin), {} / {} (user)",
                ADMIN_EMAIL, DEMO_PASSWORD, USER_EMAIL, DEMO_PASSWORD);
    }

    private Room room(String name, String location, int capacity, String description, String... amenities) {
        Room room = new Room(name, location, capacity);
        room.setDescription(description);
        room.replaceAmenities(Set.of(amenities));
        return rooms.save(room);
    }

    private Booking book(Room room, User user, LocalDate date, String start, String end, String title, int attendees) {
        Booking b = new Booking(room, user);
        b.setTitle(title);
        b.setStartTime(date.atTime(LocalTime.parse(start)));
        b.setEndTime(date.atTime(LocalTime.parse(end)));
        b.setAttendees(attendees);
        return bookings.save(b);
    }
}
