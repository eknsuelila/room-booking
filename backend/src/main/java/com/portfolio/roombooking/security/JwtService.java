package com.portfolio.roombooking.security;

import com.portfolio.roombooking.user.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Creates and validates the JSON Web Tokens (JWT) used for stateless authentication.
 *
 * <p><b>What is a JWT?</b> Three Base64 parts separated by dots: {@code header.payload.signature}.
 * The payload holds "claims" (facts) such as the user's email and role. The signature is an
 * HMAC-SHA256 of header + payload computed with a secret key only the server knows. Anyone can READ
 * a JWT (it is not encrypted - never put secrets in it), but nobody can CHANGE it without
 * invalidating the signature. So when a token comes back, the server can trust its claims without
 * looking anything up in a session store - that is what "stateless" means.
 *
 * <p><b>Where it fits:</b> {@code AuthService} calls {@link #generateToken} after a successful login;
 * {@link JwtAuthenticationFilter} calls {@link #extractEmail} on every subsequent request.
 *
 * <p>{@code @Service} marks this as a Spring-managed singleton bean, so it can be injected anywhere.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    /**
     * {@code @Value("${...}")} injects settings from application.yml (or environment variables).
     * The secret is Base64 so that it can contain arbitrary bytes; HS256 requires at least 256 bits.
     */
    public JwtService(@Value("${app.jwt.secret}") String base64Secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Builds a signed token for the user.
     *
     * <p>Claims:
     * <ul>
     *   <li>{@code sub} (subject) - the email, used to load the user on each request;</li>
     *   <li>{@code name}, {@code role} - convenience copies for clients. The SERVER never trusts the
     *       role claim for authorization: the filter re-loads the user from the database, so demoting
     *       an admin takes effect immediately rather than when the token expires;</li>
     *   <li>{@code iat}/{@code exp} - issued-at and expiry timestamps.</li>
     * </ul>
     */
    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)                // HS256 chosen automatically from the key size
                .compact();
    }

    /**
     * Verifies the signature and expiry, then returns the email (subject).
     *
     * @throws JwtException if the token is malformed, tampered with or expired
     */
    public String extractEmail(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)     // throws if the signature or expiry is invalid
                .getPayload()
                .getSubject();
    }
}
