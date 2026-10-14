package com.portfolio.roombooking.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register}. Note there is deliberately NO "role" field: public sign-up
 * always creates a USER, so nobody can register themselves as an admin.
 * Sizes match the column lengths in V1__create_tables.sql.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100, message = "must be at least 8 characters") String password) {
}
