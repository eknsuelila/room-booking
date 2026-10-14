package com.portfolio.roombooking.room.dto;

import jakarta.validation.constraints.*;

import java.util.Set;

/**
 * Body of {@code POST /api/rooms} and {@code PUT /api/rooms/{id}} (admin only).
 *
 * <p>Bean Validation annotations declare the rules right on the fields; they are checked when the
 * controller parameter has {@code @Valid}. The limits mirror the database column sizes.
 * {@code Set<@NotBlank @Size(max = 50) String>} validates EACH amenity in the set.
 */
public record RoomRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 150) String location,
        @NotNull @Min(1) @Max(500) Integer capacity,
        Set<@NotBlank @Size(max = 50) String> amenities,
        @Size(max = 1000) String description) {
}
