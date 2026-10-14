package com.portfolio.roombooking.auth;

import com.portfolio.roombooking.auth.dto.AuthResponse;
import com.portfolio.roombooking.auth.dto.LoginRequest;
import com.portfolio.roombooking.auth.dto.RegisterRequest;
import com.portfolio.roombooking.auth.dto.UserResponse;
import com.portfolio.roombooking.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication endpoints under {@code /api/auth}.
 *
 * <p>{@code @RestController} = a controller whose method return values are written straight to the
 * response body as JSON. {@code @RequestMapping("/api/auth")} prefixes every path in the class.
 *
 * <p>Controllers stay thin on purpose: they translate HTTP into method calls and back; the actual
 * logic lives in {@link AuthService}, which is easier to unit test.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * {@code POST /api/auth/register} (public). Returns 201 Created with a token.
     * {@code @Valid} triggers bean validation of the body; failures become a 400 with fieldErrors.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    /** {@code POST /api/auth/login} (public). 401 on wrong credentials. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * {@code GET /api/auth/me}: who am I? {@code @AuthenticationPrincipal} injects the User that the
     * JWT filter put into the security context.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal User user) {
        return UserResponse.from(user);
    }
}
