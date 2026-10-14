package com.portfolio.roombooking.booking;

import com.portfolio.roombooking.room.Room;
import com.portfolio.roombooking.user.User;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * A reservation of one {@link Room} by one {@link User} for a time range on a single day
 * (table {@code bookings}).
 *
 * <p><b>Time-zone design decision.</b> {@link #startTime}/{@link #endTime} are {@link LocalDateTime}
 * values: "office wall-clock time" with NO time zone, stored in a {@code TIMESTAMP} (without time zone)
 * column. "10:00 on 15 Oct" means 10:00 on the clock on the meeting-room wall, full stop.
 * <ul>
 *   <li>Why: a meeting room is a physical place in one building, in one time zone. Business rules
 *       like "08:00-20:00" and "15-minute steps" are naturally wall-clock rules, and the UI shows
 *       exactly what the user typed. No conversion bugs, no daylight-saving surprises in tests.</li>
 *   <li>Trade-off: a company with offices in several time zones would need a zone per room (or per
 *       building) and to store instants ({@code Instant}/{@code timestamptz}). This is listed as a
 *       possible improvement in docs/HOW-IT-WORKS.md.</li>
 *   <li>"Now" for rules such as "not in the past" comes from the injected {@code Clock} in the
 *       server's default zone, which should be set to the office's zone (TZ env var in Docker).</li>
 * </ul>
 * By contrast {@link #createdAt} is an {@link Instant} (a real point on the global timeline) because it
 * is an audit timestamp, not a calendar slot.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * {@code @ManyToOne}: many bookings point to one room; stored as the {@code room_id} foreign key.
     * {@code LAZY} = the Room is only loaded from the DB when we actually use it. Queries that always
     * need it use {@code join fetch} / {@code @EntityGraph} to load both in one SQL statement.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** The owner: the only non-admin allowed to reschedule or cancel this booking. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String description;

    /** Inclusive start, office wall-clock time. */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * EXCLUSIVE end. A 10:00-11:00 booking occupies [10:00, 11:00): the 11:00-12:00 booking that
     * follows it does not overlap. See BookingRepository for the half-open interval formula.
     */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private int attendees;

    /** Stored as text ('CONFIRMED'/'CANCELLED'), see {@link BookingStatus}. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Booking() {
    }

    public Booking(Room room, User user) {
        this.room = room;
        this.user = user;
    }

    /** Called by Hibernate right before the INSERT. */
    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** True if this booking belongs to the given user (compared by id; entities may be different instances). */
    public boolean isOwnedBy(User other) {
        return user.getId() != null && user.getId().equals(other.getId());
    }

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public int getAttendees() { return attendees; }
    public void setAttendees(int attendees) { this.attendees = attendees; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
