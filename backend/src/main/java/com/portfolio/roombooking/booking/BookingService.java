package com.portfolio.roombooking.booking;

import com.portfolio.roombooking.booking.dto.BookingRequest;
import com.portfolio.roombooking.booking.dto.BookingResponse;
import com.portfolio.roombooking.common.BusinessRuleException;
import com.portfolio.roombooking.common.ConflictException;
import com.portfolio.roombooking.common.PageResponse;
import com.portfolio.roombooking.common.ResourceNotFoundException;
import com.portfolio.roombooking.room.Room;
import com.portfolio.roombooking.room.RoomRepository;
import com.portfolio.roombooking.user.User;
import com.portfolio.roombooking.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.portfolio.roombooking.booking.BookingSpecifications.*;

/**
 * All booking business logic: create, reschedule, cancel, list.
 *
 * <p><b>Where it fits:</b> {@code BookingController} -> <b>BookingService</b> -> repositories -> DB.
 * The controller only deals with HTTP; this class enforces every rule and throws exceptions that
 * {@code GlobalExceptionHandler} turns into 400/403/404/409 responses.
 *
 * <p>{@code @Transactional} on the class makes EVERY public method run in a database transaction:
 * Spring opens it before the method and commits after it returns, or rolls back if an unchecked
 * exception escapes. This is essential for booking: the row locks taken in
 * {@link #createOrReschedule} are held until that commit, which is what prevents double bookings.
 *
 * <p>Validation order when booking (cheapest and most user-relevant first):
 * <ol>
 *   <li>time rules (pure Java, no DB) - {@link BookingTimeRules} -> 400</li>
 *   <li>lock the room row, then the owner's user row (serializes competing requests)</li>
 *   <li>room must be active, attendees &le; capacity -> 400</li>
 *   <li>no overlapping CONFIRMED booking in that room -> 409</li>
 *   <li>the owner has no other overlapping CONFIRMED booking -> 409</li>
 * </ol>
 */
@Service
@Transactional
public class BookingService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingRepository bookings;
    private final RoomRepository rooms;
    private final UserRepository users;
    private final BookingTimeRules timeRules;
    private final Clock clock;

    public BookingService(BookingRepository bookings, RoomRepository rooms, UserRepository users,
                          BookingTimeRules timeRules, Clock clock) {
        this.bookings = bookings;
        this.rooms = rooms;
        this.users = users;
        this.timeRules = timeRules;
        this.clock = clock;
    }

    /** Creates a booking owned by {@code currentUser}. */
    public BookingResponse create(User currentUser, BookingRequest request) {
        return BookingResponse.from(createOrReschedule(currentUser, null, request));
    }

    /**
     * Moves/edits an existing booking (new time, room, title or attendees). Owners may reschedule
     * their own future bookings; admins may reschedule anyone's. The booking keeps its owner.
     */
    public BookingResponse reschedule(User currentUser, Long bookingId, BookingRequest request) {
        Booking existing = findBooking(bookingId);
        checkCanModify(currentUser, existing);
        return BookingResponse.from(createOrReschedule(existing.getUser(), existing, request));
    }

    /**
     * Cancels (soft-deletes) a booking, which frees its slot immediately.
     * Same permission rules as {@link #reschedule}.
     */
    public BookingResponse cancel(User currentUser, Long bookingId) {
        Booking booking = findBooking(bookingId);
        checkCanModify(currentUser, booking);
        booking.setStatus(BookingStatus.CANCELLED);
        // No explicit save() needed: the entity is "managed" inside this transaction, so Hibernate
        // detects the change ("dirty checking") and issues the UPDATE at commit.
        return BookingResponse.from(booking);
    }

    /** One booking; visible to its owner and to admins only. */
    @Transactional(readOnly = true)
    public BookingResponse get(User currentUser, Long bookingId) {
        Booking booking = findBooking(bookingId);
        if (!currentUser.isAdmin() && !booking.isOwnedBy(currentUser)) {
            throw new AccessDeniedException("You can only view your own bookings");
        }
        return BookingResponse.from(booking);
    }

    /**
     * The current user's bookings.
     *
     * @param scope {@code "upcoming"} (confirmed, not yet ended, soonest first) or {@code "past"}
     *              (ended or cancelled, most recent first)
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(User currentUser, String scope) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> result = switch (scope == null ? "upcoming" : scope.toLowerCase()) {
            case "upcoming" -> bookings.findUpcomingForUser(currentUser.getId(), now);
            case "past" -> bookings.findPastForUser(currentUser.getId(), now);
            default -> throw new BusinessRuleException("scope must be 'upcoming' or 'past'");
        };
        return result.stream().map(BookingResponse::from).toList();
    }

    /** Admin list of all bookings with optional filters and paging. */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> search(LocalDate date, Long roomId, BookingStatus status, Pageable pageable) {
        var spec = onDate(date).and(inRoom(roomId)).and(hasStatus(status));
        return PageResponse.from(bookings.findAll(spec, pageable).map(BookingResponse::from));
    }

    // ------------------------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------------------------

    /**
     * Shared logic for create ({@code existing == null}) and reschedule.
     *
     * @param owner    who the booking belongs to (current user on create; unchanged on reschedule)
     * @param existing the booking being moved, or null for a new one
     */
    private Booking createOrReschedule(User owner, Booking existing, BookingRequest request) {
        LocalDateTime start = request.startTime();
        LocalDateTime end = request.endTime();

        // 1. Pure time rules first: no point touching the database for a 07:00 or 7-minute request.
        timeRules.validate(start, end);

        // 2. Take the locks, ALWAYS in the same order (room, then user). A consistent order is what
        //    prevents deadlocks: two transactions can never each hold one lock the other one needs.
        //    The locks are released when this @Transactional method commits or rolls back.
        Room room = rooms.findByIdForUpdate(request.roomId())
                .orElseThrow(() -> new ResourceNotFoundException("Room " + request.roomId() + " not found"));
        User lockedOwner = users.findByIdForUpdate(owner.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User " + owner.getId() + " not found"));

        // 3. Room-level rules (need the room from the DB).
        if (!room.isActive()) {
            throw new BusinessRuleException("Room '" + room.getName() + "' is not available for booking");
        }
        if (request.attendees() > room.getCapacity()) {
            throw new BusinessRuleException("Room '%s' holds at most %d people (you requested %d)"
                    .formatted(room.getName(), room.getCapacity(), request.attendees()));
        }

        // 4 + 5. Overlap checks. When rescheduling, the booking must not conflict with ITSELF.
        long excludeId = existing == null ? BookingRepository.NO_ID : existing.getId();
        checkNoConflicts(room, lockedOwner, start, end, excludeId);

        Booking booking = existing != null ? existing : new Booking(room, lockedOwner);
        booking.setRoom(room);
        booking.setTitle(request.title().trim());
        booking.setDescription(blankToNull(request.description()));
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setAttendees(request.attendees());
        // saveAndFlush sends the INSERT/UPDATE now (still inside the transaction, locks still held),
        // so the generated id is available for the response.
        return bookings.saveAndFlush(booking);
    }

    /**
     * Throws 409 if the room, or the owner, already has a CONFIRMED booking overlapping [start, end).
     * The messages reveal only times and room names - never another person's meeting title.
     */
    private void checkNoConflicts(Room room, User owner, LocalDateTime start, LocalDateTime end, long excludeId) {
        List<Booking> roomConflicts = bookings.findRoomConflicts(room.getId(), start, end, excludeId);
        if (!roomConflicts.isEmpty()) {
            Booking c = roomConflicts.get(0);
            throw new ConflictException("'%s' is already booked from %s to %s on %s. Please choose another time or room."
                    .formatted(room.getName(), c.getStartTime().format(HH_MM), c.getEndTime().format(HH_MM),
                            c.getStartTime().toLocalDate()));
        }
        List<Booking> userConflicts = bookings.findUserConflicts(owner.getId(), start, end, excludeId);
        if (!userConflicts.isEmpty()) {
            Booking c = userConflicts.get(0);
            throw new ConflictException("You already have a booking in '%s' from %s to %s that overlaps this time"
                    .formatted(c.getRoom().getName(), c.getStartTime().format(HH_MM), c.getEndTime().format(HH_MM)));
        }
    }

    /**
     * Permission + state check before cancelling or rescheduling:
     * <ul>
     *   <li>only the owner or an admin -> otherwise 403;</li>
     *   <li>must still be CONFIRMED -> otherwise 409 (it changed under you);</li>
     *   <li>must not have started yet -> otherwise 400 (history is immutable, for admins too).</li>
     * </ul>
     */
    private void checkCanModify(User currentUser, Booking booking) {
        if (!currentUser.isAdmin() && !booking.isOwnedBy(currentUser)) {
            // Spring Security's AccessDeniedException -> 403 via GlobalExceptionHandler.
            throw new AccessDeniedException("You can only change your own bookings");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ConflictException("This booking has already been cancelled");
        }
        if (!booking.getStartTime().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("Bookings that have already started or ended cannot be changed");
        }
    }

    private Booking findBooking(Long id) {
        return bookings.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + id + " not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
