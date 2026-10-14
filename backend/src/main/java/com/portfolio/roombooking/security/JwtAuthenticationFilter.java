package com.portfolio.roombooking.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once for EVERY HTTP request, before any controller, and turns a valid
 * {@code Authorization: Bearer <token>} header into a logged-in user.
 *
 * <p><b>Where it fits in the request flow:</b>
 * <pre>
 *  HTTP request
 *    -> Spring Security filter chain
 *         -> JwtAuthenticationFilter (this class): token valid? put the User into the SecurityContext
 *         -> AuthorizationFilter: is the URL public, or is someone authenticated? else 401
 *    -> DispatcherServlet -> @RestController method (@PreAuthorize checked here) -> service -> DB
 * </pre>
 *
 * <p>An invalid or expired token is simply ignored: the request continues anonymously and the
 * authorization step then answers 401. The filter itself never writes an error response.
 *
 * <p>Not annotated with {@code @Component} on purpose: SecurityConfig creates it with {@code new} and
 * inserts it into the security chain. As a {@code @Component} Spring Boot would ALSO register it as
 * a regular servlet filter and it would run twice.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String email = jwtService.extractEmail(header.substring(BEARER_PREFIX.length()));
                // Re-load the user from the DB: picks up role changes and rejects deleted accounts.
                UserDetails user = userDetailsService.loadUserByUsername(email);
                var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // From here on, @AuthenticationPrincipal and @PreAuthorize see this user.
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                // Invalid token: continue unauthenticated -> protected endpoints will answer 401.
            }
        }
        chain.doFilter(request, response);   // always pass the request on to the next filter
    }
}
