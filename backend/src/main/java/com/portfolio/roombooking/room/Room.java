package com.portfolio.roombooking.room;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A bookable meeting room (table {@code rooms}, amenities in {@code room_amenities}).
 *
 * <p><b>Where it fits:</b> admins create/edit rooms through {@code RoomController}; everyone browses
 * them; {@code BookingService} reads capacity and the {@link #active} flag when validating a booking
 * and LOCKS the room row while checking for overlaps (see {@code RoomRepository#findByIdForUpdate}).
 *
 * <p>Rooms that have bookings are never hard-deleted (the bookings' history would point at nothing);
 * admins deactivate them instead, which hides them from booking but keeps the history intact.
 */
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique (case-insensitively checked in RoomService, strictly enforced by the DB UNIQUE constraint). */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /** Free text such as "Floor 2, North wing". */
    @Column(nullable = false, length = 150)
    private String location;

    /** Maximum number of people; a booking's attendees may not exceed it. */
    @Column(nullable = false)
    private int capacity;

    @Column(length = 1000)
    private String description;

    /**
     * Equipment available in the room, e.g. "Projector", "Whiteboard".
     *
     * <p>{@code @ElementCollection} maps a collection of simple values (not entities) to a separate
     * table. Each String becomes one row in {@code room_amenities(room_id, amenity)}:
     * <ul>
     *   <li>{@code @CollectionTable} names that table and its foreign-key column;</li>
     *   <li>{@code @Column} names the column holding the value itself.</li>
     * </ul>
     * The values have no identity of their own: they live and die with the room. That is the
     * difference from a {@code @OneToMany} to an Amenity entity, which would be overkill for labels.
     *
     * <p>{@code LAZY} means the amenities are only loaded when first accessed. List queries use an
     * {@code @EntityGraph} to load them in the same SQL query and avoid the "N+1 queries" problem.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "room_amenities", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "amenity", nullable = false, length = 50)
    private Set<String> amenities = new HashSet<>();

    /** Inactive rooms are still listed for admins but cannot receive new bookings. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected Room() {
    }

    public Room(String name, String location, int capacity) {
        this.name = name;
        this.location = location;
        this.capacity = capacity;
    }

    /**
     * JPA lifecycle callback: Hibernate calls this just before the INSERT, so createdAt is always set
     * without every caller having to remember it.
     */
    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<String> getAmenities() { return amenities; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Replaces the amenities. We mutate the existing set instead of assigning a new one because
     * Hibernate tracks changes through its own collection wrapper (PersistentSet); replacing the
     * reference would force it to delete and re-insert every row.
     */
    public void replaceAmenities(Set<String> newAmenities) {
        amenities.clear();
        amenities.addAll(newAmenities);
    }
}
