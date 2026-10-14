package com.portfolio.roombooking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.roombooking.security.JsonAccessDeniedHandler;
import com.portfolio.roombooking.security.JwtAuthenticationFilter;
import com.portfolio.roombooking.security.JwtService;
import com.portfolio.roombooking.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * All Spring Security settings in one place.
 *
 * <p>Two layers of authorization are used:
 * <ol>
 *   <li><b>URL rules</b> (below, in {@link #securityFilterChain}): which paths are public and which
 *       require ANY logged-in user. Checked in the filter chain, before controllers run.</li>
 *   <li><b>Method rules</b>: {@code @PreAuthorize("hasRole('ADMIN')")} on individual controller
 *       methods, enabled by {@code @EnableMethodSecurity}. Keeping the role check next to the endpoint
 *       makes it obvious when reading a controller who may call it.</li>
 * </ol>
 * Finer, data-dependent rules ("only the owner may cancel a booking") live in the services, because
 * they need the booking from the database.
 *
 * <p>{@code @EnableMethodSecurity} switches on processing of {@code @PreAuthorize}. Without it the
 * annotations would be silently ignored - a classic security bug - which is why there is an
 * integration test asserting that a USER gets 403 on an admin endpoint.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Endpoints anyone may call without a token. Everything else needs authentication. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/login",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/h2-console/**",
            "/error"
    };

    /**
     * The chain of security filters every request passes through.
     *
     * @param http               builder provided by Spring Security
     * @param jwtService         used by our JWT filter to validate tokens
     * @param userDetailsService loads the User entity for the email inside a token
     * @param objectMapper       Spring's configured Jackson mapper, used to write JSON 403 bodies
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   UserDetailsService userDetailsService,
                                                   ObjectMapper objectMapper) throws Exception {
        return http
                // CSRF attacks abuse cookies that browsers send automatically. We authenticate with a
                // header the JavaScript adds explicitly, so CSRF protection is unnecessary here.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())                       // uses the corsConfigurationSource bean below
                .headers(h -> h.frameOptions(f -> f.sameOrigin()))     // the H2 console uses frames (dev only)
                // STATELESS: never create an HTTP session. Each request must carry its own JWT.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        // Defence in depth: admin-only URL prefix (stats). Controllers ALSO use @PreAuthorize.
                        .requestMatchers("/api/stats/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        // Not logged in -> plain 401 (the frontend interceptor reacts by logging out).
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // Logged in but wrong role for a URL rule -> JSON 403.
                        .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
                // Run our JWT filter before Spring's username/password filter, so the user is already
                // authenticated by the time authorization rules are checked.
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * How Spring Security finds a user by "username" (our email). A lambda is enough because
     * {@link UserDetailsService} has a single method.
     */
    @Bean
    public UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    /**
     * BCrypt: a deliberately SLOW, salted hash. The random salt makes identical passwords hash
     * differently, and the slowness makes brute-forcing a stolen database expensive.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS (Cross-Origin Resource Sharing): browsers only let JavaScript from origin A call an API on
     * origin B if B explicitly allows A. Origins come from {@code app.cors.allowed-origins}.
     * (With the dev proxy and nginx the browser sees ONE origin, so this mostly matters when the
     * frontend is hosted on a different domain than the API, e.g. on Render.)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
