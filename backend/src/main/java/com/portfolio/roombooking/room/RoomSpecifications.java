package com.portfolio.roombooking.room;

import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

/**
 * Small, composable query filters for the room list ({@code GET /api/rooms}).
 *
 * <p>A {@link Specification} is a function that produces one WHERE-clause predicate. Returning
 * {@code null} means "no condition", so optional filters can be chained with {@code .and(...)}
 * without any if/else in the service:
 * <pre>  isActive(true).and(minCapacity(null)).and(hasAllAmenities(["Projector"]))</pre>
 * becomes {@code WHERE active = true AND 'Projector' MEMBER OF amenities}.
 */
final class RoomSpecifications {

    private RoomSpecifications() {
    }

    /** Only active rooms when {@code activeOnly} is true; no condition otherwise. */
    static Specification<Room> activeOnly(boolean activeOnly) {
        return (root, query, cb) -> activeOnly ? cb.isTrue(root.get("active")) : null;
    }

    static Specification<Room> minCapacity(Integer minCapacity) {
        return (root, query, cb) -> minCapacity == null ? null
                : cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity);
    }

    /**
     * Room must have EVERY requested amenity. {@code isMember} generates a sub-query against the
     * room_amenities collection table, so rows are not duplicated by a join.
     */
    static Specification<Room> hasAllAmenities(Collection<String> amenities) {
        return (root, query, cb) -> {
            if (amenities == null || amenities.isEmpty()) {
                return null;
            }
            return cb.and(amenities.stream()
                    .map(a -> cb.isMember(a, root.<Collection<String>>get("amenities")))
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
