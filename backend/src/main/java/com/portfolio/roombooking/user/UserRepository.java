package com.portfolio.roombooking.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Data access for {@link User}.
 *
 * <p>This is only an interface: Spring Data JPA generates the implementation at startup.
 * Extending {@link JpaRepository} gives us save/findById/findAll/delete/count... for free, and
 * methods named like {@code findByEmailIgnoreCase} are turned into SQL automatically by parsing the
 * method name ("derived queries").
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** Login lookup. "IgnoreCase" so that Alice@Example.com and alice@example.com are the same account. */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Loads a user with a database row lock ({@code SELECT ... FOR UPDATE}) that is held until the
     * surrounding transaction ends.
     *
     * <p>Used by BookingService to make the rule "a user cannot have two overlapping bookings" safe
     * against two simultaneous requests from the same user (e.g. a double-clicked button) that target
     * DIFFERENT rooms - the room lock alone would not serialize those. See
     * {@code RoomRepository#findByIdForUpdate} for a full explanation of pessimistic locking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(Long id);
}
