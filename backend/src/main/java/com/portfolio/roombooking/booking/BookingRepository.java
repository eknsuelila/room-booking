package com.portfolio.roombooking.booking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Booking}, including the all-important overlap queries.
 *
 * <h2>Overlap detection with half-open intervals</h2>
 * Every booking occupies the half-open interval {@code [start, end)}: the start minute belongs to it,
 * the end minute does not. Two intervals A and B overlap if and only if
 * <pre>
 *     A.start &lt; B.end   AND   A.end &gt; B.start
 * </pre>
 * Picture the existing booking E = 10:00-11:00 and some candidates N:
 * <pre>
 *            09:00     10:00     11:00     12:00
 *   E                    [=========)
 *   N1  [=========)                            09:00-10:00  10:00 &lt; 10:00? no  -> OK (touches)
 *   N2                             [=========) 11:00-12:00  11:00 &gt; 11:00? no  -> OK (touches)
 *   N3            [=========)                  09:30-10:30  both true          -> CONFLICT
 *   N4                         [=========)     10:30-11:30  both true          -> CONFLICT
 *   N5                  [===)                  10:15-10:45  inside E           -> CONFLICT
 *   N6       [=====================)           09:00-12:00  encloses E         -> CONFLICT
 * </pre>
 * The two strict comparisons ({@code <}, {@code >}) are what allow back-to-back meetings. This single
 * formula covers every case (partial, inside, enclosing, identical), which is why it beats a
 * hand-written list of "if start is between..." checks. Each row above is an integration test.
 *
 * <p>{@code excludeId}: when RESCHEDULING booking #42 we must not report #42 as conflicting with its
 * own new time, so it is excluded. For brand-new bookings callers pass {@link #NO_ID} (-1), which matches
 * no row. (A sentinel avoids {@code (:id is null or ...)}, which PostgreSQL cannot type-check.)
 */
public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    /** Sentinel for "exclude nothing" in the overlap queries. */
    long NO_ID = -1L;

    /** CONFIRMED bookings of a room that overlap [start, end), other than {@code excludeId}. */
    @Query("""
            select b from Booking b
            where b.room.id = :roomId
              and b.status = com.portfolio.roombooking.booking.BookingStatus.CONFIRMED
              and b.startTime < :end
              and b.endTime > :start
              and b.id <> :excludeId
            order by b.startTime
            """)
    List<Booking> findRoomConflicts(Long roomId, LocalDateTime start, LocalDateTime end, long excludeId);

    /**
     * CONFIRMED bookings OF A USER (in any room) that overlap [start, end). Enforces "you cannot be in
     * two meetings at once". {@code join fetch b.room} loads the room name for the error message.
     */
    @Query("""
            select b from Booking b join fetch b.room
            where b.user.id = :userId
              and b.status = com.portfolio.roombooking.booking.BookingStatus.CONFIRMED
              and b.startTime < :end
              and b.endTime > :start
              and b.id <> :excludeId
            order by b.startTime
            """)
    List<Booking> findUserConflicts(Long userId, LocalDateTime start, LocalDateTime end, long excludeId);

    /** A room's confirmed bookings that overlap [from, to) - used for the day timeline. */
    @Query("""
            select b from Booking b join fetch b.user
            where b.room.id = :roomId
              and b.status = com.portfolio.roombooking.booking.BookingStatus.CONFIRMED
              and b.startTime < :to
              and b.endTime > :from
            order by b.startTime
            """)
    List<Booking> findRoomSchedule(Long roomId, LocalDateTime from, LocalDateTime to);

    /** "Upcoming" tab: my confirmed bookings that have not ended yet, soonest first. */
    @Query("""
            select b from Booking b join fetch b.room join fetch b.user
            where b.user.id = :userId
              and b.status = com.portfolio.roombooking.booking.BookingStatus.CONFIRMED
              and b.endTime > :now
            order by b.startTime asc
            """)
    List<Booking> findUpcomingForUser(Long userId, LocalDateTime now);

    /** "Past" tab: my bookings that have ended OR were cancelled, most recent first. */
    @Query("""
            select b from Booking b join fetch b.room join fetch b.user
            where b.user.id = :userId
              and (b.endTime <= :now
                   or b.status = com.portfolio.roombooking.booking.BookingStatus.CANCELLED)
            order by b.startTime desc
            """)
    List<Booking> findPastForUser(Long userId, LocalDateTime now);

    /** Bookings (any status) that START in [from, to), with their rooms - raw data for the stats page. */
    @Query("""
            select b from Booking b join fetch b.room
            where b.startTime >= :from and b.startTime < :to
            """)
    List<Booking> findStartingBetween(LocalDateTime from, LocalDateTime to);

    /** Loads one booking with its room and user in a single query. */
    @Query("select b from Booking b join fetch b.room join fetch b.user where b.id = :id")
    Optional<Booking> findWithDetailsById(Long id);

    /** Does the room have ANY booking (even cancelled)? If so it cannot be deleted, only deactivated. */
    boolean existsByRoomId(Long roomId);

    /** Admin list with dynamic filters; the entity graph avoids N+1 queries for room and user names. */
    @Override
    @EntityGraph(attributePaths = {"room", "user"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);
}
