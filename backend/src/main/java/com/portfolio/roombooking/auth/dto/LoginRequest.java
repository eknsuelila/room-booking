package com.portfolio.roombooking.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/auth/login}. {@code @NotBlank} rejects null, "" and "   " - checked when the
 * controller parameter is annotated with {@code @Valid}.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}
