package com.portfolio.roombooking.room.dto;

import com.portfolio.roombooking.room.Room;

import java.util.List;

/**
 * How a room is sent to clients. Amenities are a sorted list so the JSON (and the UI) is stable.
 * Must be created inside a transaction because reading {@code getAmenities()} may lazy-load them.
 */
public record RoomResponse(Long id, String name, String location, int capacity, List<String> amenities,
                           String description, boolean active) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getLocation(), room.getCapacity(),
                room.getAmenities().stream().sorted().toList(), room.getDescription(), room.isActive());
    }
}
