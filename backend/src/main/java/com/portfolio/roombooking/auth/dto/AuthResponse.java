package com.portfolio.roombooking.auth.dto;

/**
 * Response of register and login: the JWT to send on later requests, plus the user's public profile
 * (including the role, so the Angular app can show admin menus without decoding the token).
 */
public record AuthResponse(String token, UserResponse user) {
}
