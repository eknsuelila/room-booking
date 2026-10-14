package com.portfolio.roombooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the application-wide {@link Clock}.
 *
 * <p>Many booking rules depend on "now" (no bookings in the past, upcoming vs past tabs, stats for
 * "this week"). If services called {@code LocalDateTime.now()} directly, tests would behave differently
 * depending on when they run. Instead services receive a {@code Clock} and call
 * {@code LocalDateTime.now(clock)}; unit tests pass {@code Clock.fixed(...)} and get a frozen,
 * predictable "now".
 *
 * <p>{@code @Configuration} marks a class that defines beans; each {@code @Bean} method's return value
 * is registered in the Spring container and injected wherever that type is requested.
 *
 * <p>{@code systemDefaultZone()} uses the server's time zone, which is how we interpret the office
 * wall-clock times of bookings (see Booking for the time-zone design decision). In Docker you can set
 * the zone with the TZ environment variable.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
