package com.portfolio.roombooking.auth;

import com.portfolio.roombooking.auth.dto.AuthResponse;
import com.portfolio.roombooking.auth.dto.LoginRequest;
import com.portfolio.roombooking.auth.dto.RegisterRequest;
import com.portfolio.roombooking.auth.dto.UserResponse;
import com.portfolio.roombooking.common.ConflictException;
import com.portfolio.roombooking.security.JwtService;
import com.portfolio.roombooking.user.Role;
import com.portfolio.roombooking.user.User;
import com.portfolio.roombooking.user.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login logic. Called by {@link AuthController}; returns a JWT on success.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Constructor injection: Spring passes in the beans; fields can be final and tests can pass mocks. */
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Creates a USER account and logs it in immediately.
     *
     * <p>{@code @Transactional}: the method runs inside one database transaction - either everything
     * it writes is committed, or (if an exception escapes) everything is rolled back.
     *
     * @throws ConflictException if the email is already registered (409)
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        User user = users.save(new User(request.name().trim(), email,
                passwordEncoder.encode(request.password()), Role.USER));
        return new AuthResponse(jwtService.generateToken(user), UserResponse.from(user));
    }

    /**
     * Checks email + password and returns a fresh token.
     *
     * <p>The error message is the same for "unknown email" and "wrong password" on purpose: a
     * different message would let attackers discover which emails have accounts.
     * {@code readOnly = true} is a hint that lets Hibernate skip dirty-checking.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return new AuthResponse(jwtService.generateToken(user), UserResponse.from(user));
    }
}
