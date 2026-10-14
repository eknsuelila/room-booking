package com.portfolio.roombooking.room;

import com.portfolio.roombooking.booking.Booking;
import com.portfolio.roombooking.booking.BookingRepository;
import com.portfolio.roombooking.booking.BookingTimeRules;
import com.portfolio.roombooking.common.BusinessRuleException;
import com.portfolio.roombooking.common.ConflictException;
import com.portfolio.roombooking.common.ResourceNotFoundException;
import com.portfolio.roombooking.room.dto.RoomRequest;
import com.portfolio.roombooking.room.dto.RoomResponse;
import com.portfolio.roombooking.room.dto.ScheduleEntryResponse;
import com.portfolio.roombooking.user.User;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.portfolio.roombooking.room.RoomSpecifications.*;

/**
 * Room catalogue logic: browsing/filtering, availability search, day schedule, and admin CRUD.
 *
 * <p><b>Where it fits:</b> {@code RoomController} -> <b>RoomService</b> -> RoomRepository / BookingRepository.
 * Role checks (admin-only writes) are done with {@code @PreAuthorize} in the controller; this class
 * focuses on data rules (unique names, cannot delete a room with history, privacy of schedules).
 */
@Service
@Transactional
public class RoomService {

    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final BookingTimeRules timeRules;

    public RoomService(RoomRepository rooms, BookingRepository bookings, BookingTimeRules timeRules) {
        this.rooms = rooms;
        this.bookings = bookings;
        this.timeRules = timeRules;
    }

    /**
     * Rooms matching all given filters, sorted by name.
     *
     * @param minCapacity only rooms holding at least this many people (null = any)
     * @param amenities   rooms must have ALL of these (null/empty = any)
     * @param activeOnly  hide deactivated rooms (the admin page passes false)
     */
    @Transactional(readOnly = true)
    public List<RoomResponse> list(Integer minCapacity, List<String> amenities, boolean activeOnly) {
        var spec = activeOnly(activeOnly).and(minCapacity(minCapacity)).and(hasAllAmenities(amenities));
        return rooms.findAll(spec, Sort.by("name")).stream().map(RoomResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse get(Long id) {
        return RoomResponse.from(findRoom(id));
    }

    /** Distinct amenity names for the filter UI. */
    @Transactional(readOnly = true)
    public List<String> amenities() {
        return rooms.findAllAmenities();
    }

    /**
     * "Find a room": active rooms with enough capacity and no confirmed booking overlapping the slot.
     * The slot is validated with the SAME rules as a real booking, so the UI never offers a room for a
     * slot that would then be rejected.
     *
     * <p>Note this is a snapshot: someone may book the room a second later. That is fine - the actual
     * booking request re-checks everything under a lock and answers 409 if it was taken.
     */
    @Transactional(readOnly = true)
    public List<RoomResponse> available(LocalDateTime start, LocalDateTime end, Integer attendees) {
        timeRules.validate(start, end);
        int people = attendees == null ? 1 : attendees;
        if (people < 1) {
            throw new BusinessRuleException("Attendees must be at least 1");
        }
        return rooms.findAvailable(start, end, people).stream().map(RoomResponse::from).toList();
    }

    /**
     * The confirmed bookings of a room on one day, for the timeline view.
     * Titles are only revealed to the booking's owner and to admins (see ScheduleEntryResponse).
     */
    @Transactional(readOnly = true)
    public List<ScheduleEntryResponse> schedule(User currentUser, Long roomId, LocalDate date) {
        findRoom(roomId); // 404 for unknown rooms instead of an empty schedule
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        return bookings.findRoomSchedule(roomId, from, to).stream()
                .map(b -> toScheduleEntry(b, currentUser))
                .toList();
    }

    /** Admin: create a room. 409 if the name is taken (case-insensitive). */
    public RoomResponse create(RoomRequest request) {
        String name = request.name().trim();
        if (rooms.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A room named '" + name + "' already exists");
        }
        Room room = new Room(name, request.location().trim(), request.capacity());
        apply(room, request);
        return RoomResponse.from(rooms.saveAndFlush(room));
    }

    /**
     * Admin: edit a room. Existing bookings are NOT re-validated if the capacity shrinks; they were valid
     * when made (a possible improvement would be to warn the admin about affected bookings).
     */
    public RoomResponse update(Long id, RoomRequest request) {
        Room room = findRoom(id);
        String name = request.name().trim();
        if (rooms.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A room named '" + name + "' already exists");
        }
        room.setName(name);
        room.setLocation(request.location().trim());
        room.setCapacity(request.capacity());
        apply(room, request);
        return RoomResponse.from(rooms.saveAndFlush(room));
    }

    /**
     * Admin: activate/deactivate. Deactivating blocks NEW bookings; already confirmed future bookings
     * are kept (an admin can cancel them individually from "All bookings").
     */
    public RoomResponse setActive(Long id, boolean active) {
        Room room = findRoom(id);
        room.setActive(active);
        return RoomResponse.from(room);
    }

    /**
     * Admin: hard delete, only allowed for rooms that were never booked (e.g. created by mistake).
     * Otherwise 409 - deleting would orphan the booking history, so deactivate instead.
     */
    public void delete(Long id) {
        Room room = findRoom(id);
        if (bookings.existsByRoomId(id)) {
            throw new ConflictException("Room '" + room.getName()
                    + "' has bookings and cannot be deleted. Deactivate it instead.");
        }
        rooms.delete(room);
    }

    private Room findRoom(Long id) {
        return rooms.findById(id).orElseThrow(() -> new ResourceNotFoundException("Room " + id + " not found"));
    }

    /** Copies the optional fields; amenities are trimmed and de-duplicated. */
    private static void apply(Room room, RoomRequest request) {
        room.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        Set<String> amenities = request.amenities() == null ? Set.of()
                : request.amenities().stream().map(String::trim).collect(Collectors.toSet());
        room.replaceAmenities(amenities);
    }

    private static ScheduleEntryResponse toScheduleEntry(Booking b, User viewer) {
        boolean mine = b.isOwnedBy(viewer);
        boolean canSeeDetails = mine || viewer.isAdmin();
        return new ScheduleEntryResponse(
                b.getId(), b.getStartTime(), b.getEndTime(),
                canSeeDetails ? b.getTitle() : "Booked",
                canSeeDetails ? b.getUser().getName() : null,
                mine);
    }
}
