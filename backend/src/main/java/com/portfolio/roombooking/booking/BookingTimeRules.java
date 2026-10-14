package com.portfolio.roombooking.booking;

import com.portfolio.roombooking.common.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The pure TIME rules every booking (and every availability search) must satisfy.
 * They need no database, only the requested times and "now", which keeps them trivial to unit test.
 *
 * <p>Rules, in the order they are checked (the first broken one is reported):
 * <ol>
 *   <li>end is after start;</li>
 *   <li>start and end are on the same calendar day (no overnight bookings);</li>
 *   <li>both are on a 15-minute boundary (:00, :15, :30, :45) with zero seconds;</li>
 *   <li>duration is between 15 minutes and 4 hours;</li>
 *   <li>within business hours: start &ge; 08:00 and end &le; 20:00;</li>
 *   <li>the booking does not start in the past.</li>
 * </ol>
 * Why 15-minute steps? It keeps the timeline grid tidy and prevents awkward 7-minute gaps between
 * meetings that nobody can use.
 *
 * <p>{@code @Component} registers it as a Spring bean so services can have it injected.
 */
@Component
public class BookingTimeRules {

    public static final LocalTime OPENING_TIME = LocalTime.of(8, 0);
    public static final LocalTime CLOSING_TIME = LocalTime.of(20, 0);
    public static final int SLOT_MINUTES = 15;
    public static final Duration MIN_DURATION = Duration.ofMinutes(15);
    public static final Duration MAX_DURATION = Duration.ofHours(4);

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final Clock clock;

    public BookingTimeRules(Clock clock) {
        this.clock = clock;
    }

    /**
     * Validates a requested [start, end) range.
     *
     * @throws BusinessRuleException (HTTP 400) describing the first broken rule
     */
    public void validate(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new BusinessRuleException("Start and end time are required");
        }
        if (!end.isAfter(start)) {
            throw new BusinessRuleException("End time must be after start time");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new BusinessRuleException("A booking must start and end on the same day");
        }
        if (!isOnSlotBoundary(start) || !isOnSlotBoundary(end)) {
            throw new BusinessRuleException("Bookings must start and end on 15-minute boundaries (:00, :15, :30, :45)");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(MIN_DURATION) < 0) {
            throw new BusinessRuleException("A booking must last at least 15 minutes");
        }
        if (duration.compareTo(MAX_DURATION) > 0) {
            throw new BusinessRuleException("A booking cannot be longer than 4 hours");
        }
        // LocalTime comparisons: start must not be before 08:00, end must not be after 20:00.
        // end == 20:00 exactly is allowed (half-open interval: the booking stops AT closing time).
        if (start.toLocalTime().isBefore(OPENING_TIME) || end.toLocalTime().isAfter(CLOSING_TIME)) {
            throw new BusinessRuleException("Bookings must be within business hours (%s-%s)"
                    .formatted(OPENING_TIME.format(HH_MM), CLOSING_TIME.format(HH_MM)));
        }
        if (start.isBefore(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("Bookings cannot start in the past");
        }
    }

    /** True for times like 10:15:00.000 - minutes divisible by 15 and no seconds/nanoseconds. */
    static boolean isOnSlotBoundary(LocalDateTime time) {
        return time.getMinute() % SLOT_MINUTES == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }
}
