package com.portfolio.roombooking.room;

import com.portfolio.roombooking.room.dto.RoomRequest;
import com.portfolio.roombooking.room.dto.RoomResponse;
import com.portfolio.roombooking.room.dto.ScheduleEntryResponse;
import com.portfolio.roombooking.user.User;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST endpoints for rooms under {@code /api/rooms}.
 *
 * <pre>
 *  GET    /api/rooms?minCapacity&amenity&activeOnly   any user  list/filter
 *  GET    /api/rooms/amenities                        any user  distinct amenity names
 *  GET    /api/rooms/available?start&end&attendees    any user  free rooms for a slot
 *  GET    /api/rooms/{id}                             any user
 *  GET    /api/rooms/{id}/schedule?date=YYYY-MM-DD    any user  day timeline
 *  POST   /api/rooms                                  ADMIN     create
 *  PUT    /api/rooms/{id}                             ADMIN     update
 *  POST   /api/rooms/{id}/deactivate | /activate      ADMIN
 *  DELETE /api/rooms/{id}                             ADMIN     409 if it has bookings
 * </pre>
 *
 * <p>Note on path ordering: "/amenities" and "/available" are literal paths, so Spring prefers them
 * over the "/{id}" pattern - no clash even though they share the prefix.
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService service;

    public RoomController(RoomService service) {
        this.service = service;
    }

    /**
     * Example: {@code GET /api/rooms?minCapacity=6&amenity=Projector&amenity=Whiteboard}.
     * A repeated query parameter binds to a {@code List}.
     */
    @GetMapping
    public List<RoomResponse> list(@RequestParam(required = false) Integer minCapacity,
                                   @RequestParam(name = "amenity", required = false) List<String> amenities,
                                   @RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.list(minCapacity, amenities, activeOnly);
    }

    @GetMapping("/amenities")
    public List<String> amenities() {
        return service.amenities();
    }

    /**
     * Example: {@code GET /api/rooms/available?start=2026-10-15T10:00&end=2026-10-15T11:00&attendees=4}.
     * {@code ISO.DATE_TIME} parses "2026-10-15T10:00[:00]" into a LocalDateTime.
     */
    @GetMapping("/available")
    public List<RoomResponse> available(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Integer attendees) {
        return service.available(start, end, attendees);
    }

    @GetMapping("/{id}")
    public RoomResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /** Confirmed bookings of the room for one day. Titles hidden unless you own the booking or are admin. */
    @GetMapping("/{id}/schedule")
    public List<ScheduleEntryResponse> schedule(@AuthenticationPrincipal User user, @PathVariable Long id,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.schedule(user, id, date);
    }

    /** {@code @PreAuthorize}: only users with ROLE_ADMIN get past this point; others receive 403. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse create(@Valid @RequestBody RoomRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse deactivate(@PathVariable Long id) {
        return service.setActive(id, false);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomResponse activate(@PathVariable Long id) {
        return service.setActive(id, true);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
