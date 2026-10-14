package com.portfolio.roombooking.auth.dto;

import com.portfolio.roombooking.user.Role;
import com.portfolio.roombooking.user.User;

/**
 * Public view of a user. A DTO ("data transfer object") exists so the API never accidentally exposes
 * entity internals such as the password hash.
 */
public record UserResponse(Long id, String name, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
