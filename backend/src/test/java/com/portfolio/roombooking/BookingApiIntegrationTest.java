package com.portfolio.roombooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.roombooking.booking.BookingService;
import com.portfolio.roombooking.booking.dto.BookingRequest;
import com.portfolio.roombooking.common.ConflictException;
import com.portfolio.roombooking.user.Role;
import com.portfolio.roombooking.user.User;
import com.portfolio.roombooking.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * INTEGRATION tests: the whole application starts (security filter chain, controllers, services,
 * JPA/Hibernate, Flyway migrations, in-memory H2) and real HTTP requests are sent through MockMvc.
 *
 * <p>These prove what unit tests with mocks cannot: that the JPQL overlap queries are correct, that
 * {@code @PreAuthorize} is really enforced, and that errors come back as the documented JSON.
 *
 * <p>Isolation strategy: the H2 database is shared by all tests in this class, so every test creates
 * its OWN users and rooms (random names). Tests therefore never see each other's bookings and can run
 * in any order. Demo data is switched off. All booking dates are a week ahead so "not in the past"
 * never interferes, whatever time the build runs.
 */
@SpringBootTest(properties = "app.seed-demo-data=false")
@AutoConfigureMockMvc
class BookingApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BookingService bookingService;

    /** The day all test bookings happen on. */
    private final LocalDate day = LocalDate.now().plusDays(7);

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor(Role.ADMIN);
        userToken = tokenFor(Role.USER);
    }

    // ---------------------------------------------------------------- helpers

    /** Creates a fresh account with the given role directly in the DB (public sign-up only makes USERs) and logs in. */
    private String tokenFor(Role role) throws Exception {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        users.save(new User("Test " + role, email, passwordEncoder.encode("password123"), role));
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value(role.name()))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Admin creates a uniquely named room and returns its id. */
    private long createRoom(int capacity) throws Exception {
        String body = mvc.perform(post("/api/rooms").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Room %s", "location": "Floor 1", "capacity": %d,
                                 "amenities": ["Whiteboard", "Projector"]}
                                """.formatted(UUID.randomUUID(), capacity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    /** POST /api/bookings for {@link #day} between two "HH:mm" times. */
    private ResultActions book(String token, long roomId, String start, String end, int attendees) throws Exception {
        return mvc.perform(post("/api/bookings").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"roomId": %d, "title": "Meeting", "startTime": "%sT%s:00", "endTime": "%sT%s:00", "attendees": %d}
                        """.formatted(roomId, day, start, day, end, attendees)));
    }

    private long bookAndGetId(String token, long roomId, String start, String end) throws Exception {
        String body = book(token, roomId, start, end, 2).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    // ---------------------------------------------------------------- security & roles

    @Test
    void requestsWithoutAValidToken_get401() throws Exception {
        mvc.perform(get("/api/rooms")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rooms").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUsers_get403JsonOnAdminEndpoints() throws Exception {
        mvc.perform(post("/api/rooms").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sneaky\",\"location\":\"x\",\"capacity\":4}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(containsString("permission")));
        mvc.perform(get("/api/stats").header("Authorization", bearer(userToken))).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings").header("Authorization", bearer(userToken))).andExpect(status().isForbidden());
    }

    @Test
    void publicRegistration_alwaysCreatesAUser_evenIfARoleIsSent() throws Exception {
        String email = "reg-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Eve\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"ADMIN\"}"
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    // ---------------------------------------------------------------- overlap rules (real SQL)

    /**
     * Existing booking: 10:00-11:00. Each row is a candidate booking in the same room and whether it
     * must be accepted (201) or rejected as overlapping (409). See the diagram in BookingRepository.
     */
    @ParameterizedTest(name = "{0}-{1} -> {2}")
    @CsvSource({
            "09:00, 10:00, 201",   // ends exactly when the existing one starts: touching is allowed
            "11:00, 12:00, 201",   // starts exactly when the existing one ends: touching is allowed
            "09:30, 10:30, 409",   // overlaps the start
            "10:30, 11:30, 409",   // overlaps the end
            "10:15, 10:45, 409",   // completely inside
            "09:00, 12:00, 409",   // completely encloses
            "10:00, 11:00, 409"    // identical
    })
    void overlapDetection_halfOpenIntervals(String start, String end, int expectedStatus) throws Exception {
        long room = createRoom(10);
        bookAndGetId(adminToken, room, "10:00", "11:00");

        ResultActions result = book(userToken, room, start, end, 2).andExpect(status().is(expectedStatus));
        if (expectedStatus == 409) {
            result.andExpect(jsonPath("$.message").value(containsString("already booked from 10:00 to 11:00")));
        }
    }

    @Test
    void cancellingABooking_freesTheSlot() throws Exception {
        long room = createRoom(10);
        long id = bookAndGetId(adminToken, room, "14:00", "15:00");
        book(userToken, room, "14:00", "15:00", 2).andExpect(status().isConflict());

        mvc.perform(post("/api/bookings/{id}/cancel", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        book(userToken, room, "14:00", "15:00", 2).andExpect(status().isCreated());
    }

    @Test
    void reschedule_mayOverlapItsOwnOldSlot_butNotOtherBookings() throws Exception {
        long room = createRoom(10);
        long mine = bookAndGetId(userToken, room, "10:00", "11:00");
        bookAndGetId(adminToken, room, "12:00", "13:00");

        // 10:30-11:30 overlaps only the booking being moved -> allowed.
        mvc.perform(put("/api/bookings/{id}", mine).header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId": %d, "title": "Moved", "startTime": "%sT10:30:00", "endTime": "%sT11:30:00", "attendees": 2}
                                """.formatted(room, day, day)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value(day + "T10:30:00"));

        // 11:30-12:30 overlaps the admin's 12:00 booking -> 409.
        mvc.perform(put("/api/bookings/{id}", mine).header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomId": %d, "title": "Moved", "startTime": "%sT11:30:00", "endTime": "%sT12:30:00", "attendees": 2}
                                """.formatted(room, day, day)))
                .andExpect(status().isConflict());
    }

    @Test
    void aUserCannotBeInTwoRoomsAtOnce() throws Exception {
        long roomA = createRoom(10);
        long roomB = createRoom(10);
        bookAndGetId(userToken, roomA, "15:00", "16:00");

        book(userToken, roomB, "15:30", "16:30", 2)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("You already have a booking")));
    }

    /**
     * Fires two identical booking attempts at the same instant from two threads. Thanks to the
     * pessimistic lock on the room row exactly one must win; the other must get a ConflictException.
     * (Without the lock both could pass the overlap check before either inserted.)
     */
    @Test
    void concurrentBookingsOfTheSameSlot_exactlyOneWins() throws Exception {
        long room = createRoom(10);
        List<User> people = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            people.add(users.save(new User("Racer " + i, "racer-" + UUID.randomUUID() + "@example.com", "x", Role.USER)));
        }
        var request = new BookingRequest(room, "Race", null, day.atTime(9, 0), day.atTime(10, 0), 2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Object> outcomes = Collections.synchronizedList(new ArrayList<>());
        List<Future<?>> futures = new ArrayList<>();
        for (User person : people) {
            futures.add(pool.submit(() -> {
                startSignal.await();   // both threads wait here, then start together
                try {
                    outcomes.add(bookingService.create(person, request));
                } catch (ConflictException e) {
                    outcomes.add(e);
                }
                return null;
            }));
        }
        startSignal.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.stream().filter(o -> o instanceof ConflictException).count()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- other business rules

    @Test
    void timeRulesAndCapacity_return400WithAReadableMessage() throws Exception {
        long room = createRoom(4);
        book(userToken, room, "07:00", "08:00", 2).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("business hours")));
        book(userToken, room, "10:05", "11:00", 2).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("15-minute")));
        book(userToken, room, "09:00", "14:00", 2).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("4 hours")));
        book(userToken, room, "10:00", "11:00", 5).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("at most 4 people")));
        // Bean validation: missing title / zero attendees -> fieldErrors
        mvc.perform(post("/api/bookings").header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\": %d, \"title\": \"\", \"attendees\": 0}".formatted(room)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.attendees").exists())
                .andExpect(jsonPath("$.fieldErrors.startTime").exists());
    }

    @Test
    void ownership_otherUsersGet403_adminsMayCancelAnyBooking() throws Exception {
        long room = createRoom(10);
        long id = bookAndGetId(userToken, room, "16:00", "17:00");
        String otherUser = tokenFor(Role.USER);

        mvc.perform(post("/api/bookings/{id}/cancel", id).header("Authorization", bearer(otherUser)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("your own bookings")));
        mvc.perform(post("/api/bookings/{id}/cancel", id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void schedule_hidesTitlesFromOtherUsers() throws Exception {
        long room = createRoom(10);
        bookAndGetId(adminToken, room, "09:00", "10:00");

        mvc.perform(get("/api/rooms/{id}/schedule", room).param("date", day.toString())
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Booked"))
                .andExpect(jsonPath("$[0].bookedBy").doesNotExist())
                .andExpect(jsonPath("$[0].mine").value(false));
        mvc.perform(get("/api/rooms/{id}/schedule", room).param("date", day.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[0].title").value("Meeting"))
                .andExpect(jsonPath("$[0].mine").value(true));
    }

    @Test
    void availableRooms_excludeBookedAndTooSmallRooms() throws Exception {
        long booked = createRoom(10);
        long small = createRoom(2);
        long free = createRoom(10);
        bookAndGetId(adminToken, booked, "13:00", "14:00");

        mvc.perform(get("/api/rooms/available").header("Authorization", bearer(userToken))
                        .param("start", day + "T13:30").param("end", day + "T14:30").param("attendees", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) free)))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) booked))))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) small))));
    }

    @Test
    void roomsWithBookings_cannotBeDeleted_andInactiveRoomsCannotBeBooked() throws Exception {
        long room = createRoom(10);
        bookAndGetId(userToken, room, "08:00", "09:00");

        mvc.perform(delete("/api/rooms/{id}", room).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Deactivate it instead")));

        mvc.perform(post("/api/rooms/{id}/deactivate", room).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        book(userToken, room, "11:00", "12:00", 2).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not available")));

        long unused = createRoom(3);
        mvc.perform(delete("/api/rooms/{id}", unused).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void myBookings_andAdminViews() throws Exception {
        long room = createRoom(10);
        bookAndGetId(userToken, room, "17:00", "18:00");

        mvc.perform(get("/api/bookings/me").param("scope", "upcoming").header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomId").value(room));
        mvc.perform(get("/api/bookings").param("roomId", String.valueOf(room)).param("date", day.toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/stats").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.busiestHours", hasSize(12)));
    }
}
