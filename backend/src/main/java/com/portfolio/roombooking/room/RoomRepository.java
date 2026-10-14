package com.portfolio.roombooking.room;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Data access for {@link Room}. Spring Data generates the implementation at runtime.
 *
 * <p>{@link JpaSpecificationExecutor} adds {@code findAll(Specification...)}, which lets
 * {@link RoomSpecifications} combine optional filters (capacity, amenities, active) dynamically.
 */
public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {

    /**
     * Same as the inherited method, but {@code @EntityGraph} tells Hibernate to load the amenities in
     * the SAME query (a join) instead of one extra query per room when the DTOs read them.
     */
    @Override
    @EntityGraph(attributePaths = "amenities")
    List<Room> findAll(Specification<Room> spec, Sort sort);

    boolean existsByNameIgnoreCase(String name);

    /** Used on update: "is there ANOTHER room with this name?" */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Every distinct amenity across all rooms, alphabetically - feeds the filter checkboxes in the UI. */
    @Query("select distinct a from Room r join r.amenities a order by a")
    List<String> findAllAmenities();

    /**
     * Loads a room AND locks its database row until the current transaction commits or rolls back.
     *
     * <p><b>Why this exists - the double-booking race condition.</b> Booking a room is
     * "check, then insert":
     * <pre>
     *   1. SELECT overlapping bookings for room 7, 10:00-11:00   -> none
     *   2. INSERT the new booking
     * </pre>
     * If two users click "Book" at the same moment, both transactions can run step 1 before either
     * runs step 2. Both see "no overlap", both insert, and the room is double-booked:
     * <pre>
     *   Alice:  SELECT (none) ............ INSERT  COMMIT
     *   Bob:        SELECT (none) ............ INSERT  COMMIT     <- double booking!
     * </pre>
     *
     * <p><b>The fix.</b> {@code @Lock(PESSIMISTIC_WRITE)} turns the query into
     * {@code SELECT ... FROM rooms WHERE id = ? FOR UPDATE}. The database grants that row lock to one
     * transaction at a time; the second one WAITS at this line until the first commits. When Bob finally
     * gets the lock and runs his overlap check, Alice's booking is already committed and visible, so
     * Bob gets a clean 409 instead of a double booking:
     * <pre>
     *   Alice:  LOCK room 7 -> SELECT (none) -> INSERT -> COMMIT (lock released)
     *   Bob:    LOCK room 7 ....waits.......................... -> SELECT (Alice's!) -> 409
     * </pre>
     * Locking the ROOM row (rather than the bookings) works because every booking for a room must pass
     * through this lock first, and it only serializes bookings of the SAME room - other rooms are
     * unaffected. "Pessimistic" = assume conflicts will happen and prevent them up front, as opposed to
     * optimistic locking (version column) which detects them at commit time and makes one side retry.
     *
     * <p>Deliberately a plain query with no fetch join: PostgreSQL refuses {@code FOR UPDATE} on the
     * nullable side of an outer join, and we only need the row lock here.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(Long id);

    /**
     * Active rooms big enough for {@code attendees} that have NO confirmed booking overlapping
     * [start, end). The overlap condition is the same half-open formula used by BookingRepository
     * ({@code b.startTime < :end and b.endTime > :start}); see BookingRepository for the diagram.
     * Smallest suitable rooms first, so people do not grab the big boardroom for a 1:1.
     */
    @Query("""
            select r from Room r
            where r.active = true
              and r.capacity >= :attendees
              and not exists (
                  select b.id from Booking b
                  where b.room = r
                    and b.status = com.portfolio.roombooking.booking.BookingStatus.CONFIRMED
                    and b.startTime < :end
                    and b.endTime > :start)
            order by r.capacity, r.name
            """)
    List<Room> findAvailable(LocalDateTime start, LocalDateTime end, int attendees);
}
