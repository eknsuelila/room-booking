package com.portfolio.roombooking.booking;

import com.portfolio.roombooking.booking.dto.BookingRequest;
import com.portfolio.roombooking.booking.dto.BookingResponse;
import com.portfolio.roombooking.common.PageResponse;
import com.portfolio.roombooking.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST endpoints for bookings under {@code /api/bookings}.
 *
 * <pre>
 *  POST /api/bookings                   any user   create (201)
 *  GET  /api/bookings/me?scope=...      any user   my upcoming / past bookings
 *  GET  /api/bookings/{id}              owner/admin
 *  PUT  /api/bookings/{id}              owner/admin reschedule
 *  POST /api/bookings/{id}/cancel       owner/admin cancel
 *  GET  /api/bookings?date&roomId&status&page&size   ADMIN   all bookings, paged
 * </pre>
 * Every path requires authentication (SecurityConfig). Owner-vs-admin checks need the booking from
 * the DB, so they happen in {@link BookingService}; pure role checks use {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    /** Book a room. 400 for rule violations, 409 if the slot (or the user) is already taken. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@AuthenticationPrincipal User user, @Valid @RequestBody BookingRequest request) {
        return service.create(user, request);
    }

    /** Example: {@code GET /api/bookings/me?scope=past}. Defaults to upcoming. */
    @GetMapping("/me")
    public List<BookingResponse> mine(@AuthenticationPrincipal User user,
                                      @RequestParam(defaultValue = "upcoming") String scope) {
        return service.myBookings(user, scope);
    }

    @GetMapping("/{id}")
    public BookingResponse get(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.get(user, id);
    }

    /** Reschedule (PUT replaces the booking's editable fields). */
    @PutMapping("/{id}")
    public BookingResponse reschedule(@AuthenticationPrincipal User user, @PathVariable Long id,
                                      @Valid @RequestBody BookingRequest request) {
        return service.reschedule(user, id, request);
    }

    /**
     * Cancel. Modelled as a POST "action" rather than DELETE because nothing is deleted: the booking
     * stays in the history with status CANCELLED.
     */
    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return service.cancel(user, id);
    }

    /**
     * Admin overview. Example: {@code GET /api/bookings?date=2026-10-15&roomId=2&status=CONFIRMED&page=0&size=20}.
     *
     * <p>{@code @PreAuthorize("hasRole('ADMIN')")} is evaluated BEFORE the method body runs: Spring wraps
     * the controller in a proxy that checks the current user's authorities for {@code ROLE_ADMIN} and
     * throws AccessDeniedException (-> 403) otherwise.
     * {@code @DateTimeFormat(iso = DATE)} parses "2026-10-15" into a LocalDate.
     * {@code @PageableDefault} supplies page/size/sort when the client omits them.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<BookingResponse> all(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(date, roomId, status, pageable);
    }
}
