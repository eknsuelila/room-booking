package com.portfolio.roombooking.booking;

import com.portfolio.roombooking.common.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UNIT tests for the pure time rules. No Spring, no database, no mocks: just a fixed clock.
 *
 * <p>"Now" is frozen at Wednesday 2026-10-14 09:00 so "in the past" is deterministic whatever day
 * the tests actually run on.
 */
class BookingTimeRulesTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-14T09:00:00Z"), ZoneOffset.UTC);
    private final BookingTimeRules rules = new BookingTimeRules(FIXED_CLOCK);

    private static LocalDateTime at(String isoDateTime) {
        return LocalDateTime.parse(isoDateTime);
    }

    @Test
    void acceptsAValidSlot_includingOneEndingExactlyAtClosingTime() {
        assertThatCode(() -> rules.validate(at("2026-10-15T10:00"), at("2026-10-15T11:30"))).doesNotThrowAnyException();
        assertThatCode(() -> rules.validate(at("2026-10-15T08:00"), at("2026-10-15T08:15"))).doesNotThrowAnyException();
        assertThatCode(() -> rules.validate(at("2026-10-15T16:00"), at("2026-10-15T20:00"))).doesNotThrowAnyException();
    }

    /**
     * A parameterized test runs once per CSV row. Each row is: start, end, expected message fragment.
     * One test method documents every time rule in a compact table.
     */
    @ParameterizedTest(name = "{0} -> {1} rejected: {2}")
    @CsvSource({
            "2026-10-15T11:00, 2026-10-15T10:00, End time must be after start time",
            "2026-10-15T10:00, 2026-10-15T10:00, End time must be after start time",
            "2026-10-15T19:00, 2026-10-16T09:00, same day",
            "2026-10-15T10:10, 2026-10-15T11:00, 15-minute boundaries",
            "2026-10-15T10:00, 2026-10-15T10:50, 15-minute boundaries",
            "2026-10-15T10:00:30, 2026-10-15T11:00, 15-minute boundaries",
            "2026-10-15T10:00, 2026-10-15T14:15, longer than 4 hours",
            "2026-10-15T07:45, 2026-10-15T09:00, business hours",
            "2026-10-15T19:30, 2026-10-15T20:15, business hours",
            "2026-10-14T08:00, 2026-10-14T08:30, in the past",
            "2026-10-13T10:00, 2026-10-13T11:00, in the past"
    })
    void rejectsInvalidSlots(String start, String end, String expectedMessage) {
        assertThatThrownBy(() -> rules.validate(at(start), at(end)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(expectedMessage);
    }
}
