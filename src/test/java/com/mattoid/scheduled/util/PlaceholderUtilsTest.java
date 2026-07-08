package com.mattoid.scheduled.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderUtilsTest {

    @Test
    void shouldFormatLocalDateTimeWithPattern() {
        Map<String, Object> data = Map.of(
                "startTime", LocalDateTime.of(2026, 7, 7, 10, 30, 0)
        );
        String result = PlaceholderUtils.replacePlaceholders("${startTime:yyyy-MM}", data);
        assertEquals("2026-07", result);
    }

    @Test
    void shouldFormatLocalDateWithPattern() {
        Map<String, Object> data = Map.of(
                "startTime", LocalDate.of(2026, 7, 7)
        );
        String result = PlaceholderUtils.replacePlaceholders("${startTime:yyyy-MM-dd}", data);
        assertEquals("2026-07-07", result);
    }

    @Test
    void shouldFormatDateWithPattern() {
        Map<String, Object> data = Map.of(
                "startTime", new Date(126, 6, 7, 10, 30, 0) // 2026-07-07
        );
        String result = PlaceholderUtils.replacePlaceholders("${startTime:yyyy-MM-dd}", data);
        assertEquals("2026-07-07", result);
    }

    @Test
    void shouldFormatYearMonthWithPattern() {
        Map<String, Object> data = Map.of(
                "startTime", YearMonth.of(2026, 7)
        );
        String result = PlaceholderUtils.replacePlaceholders("${startTime:yyyy年MM月}", data);
        assertEquals("2026年07月", result);
    }

    @Test
    void shouldKeepOriginalBehaviorWithoutFormat() {
        Map<String, Object> data = Map.of(
                "startTime", LocalDateTime.of(2026, 7, 7, 10, 30, 0)
        );
        String result = PlaceholderUtils.replacePlaceholders("${startTime}", data);
        assertEquals("2026-07-07T10:30", result);
    }

    @Test
    void shouldKeepPlaceholderWhenKeyNotFound() {
        String result = PlaceholderUtils.replacePlaceholders("${missing:yyyy-MM}", Map.of());
        assertEquals("${missing:yyyy-MM}", result);
    }

    @Test
    void shouldResolveFirstDayOfLastMonth() {
        String result = PlaceholderUtils.replacePlaceholders("${firstDayOfLastMonth} ~ ${lastDayOfLastMonth:yyyy/MM/dd}", Map.of());
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} ~ \\d{4}/\\d{2}/\\d{2}"), result);
    }

    @Test
    void shouldResolveCurlyFirstDayOfLastWeek() {
        String result = PlaceholderUtils.replacePlaceholders("{firstDayOfLastWeek:yyyy-MM-dd} to {lastDayOfLastWeek:yyyy-MM-dd}", Map.of());
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} to \\d{4}-\\d{2}-\\d{2}"), result);
    }

    @Test
    void shouldResolveYearBoundaries() {
        String result = PlaceholderUtils.replacePlaceholders(
                "${firstDayOfThisYear} ~ ${lastDayOfThisYear}|${firstDayOfLastYear} ~ ${lastDayOfLastYear}|${firstDayOfNextYear} ~ ${lastDayOfNextYear}",
                Map.of());
        assertTrue(result.matches(
                "\\d{4}-\\d{2}-\\d{2} ~ \\d{4}-\\d{2}-\\d{2}\\|"
                        + "\\d{4}-\\d{2}-\\d{2} ~ \\d{4}-\\d{2}-\\d{2}\\|"
                        + "\\d{4}-\\d{2}-\\d{2} ~ \\d{4}-\\d{2}-\\d{2}"),
                result);
    }

    @Test
    void shouldResolveQuarterBoundaries() {
        String result = PlaceholderUtils.replacePlaceholders(
                "${firstDayOfThisQuarter} ~ ${lastDayOfThisQuarter}|${firstDayOfLastQuarter} ~ ${lastDayOfLastQuarter}",
                Map.of());
        assertTrue(result.matches(
                "\\d{4}-\\d{2}-\\d{2} ~ \\d{4}-\\d{2}-\\d{2}\\|"
                        + "\\d{4}-\\d{2}-\\d{2} ~ \\d{4}-\\d{2}-\\d{2}"),
                result);
    }

    @Test
    void shouldResolveYesterdayAndTomorrow() {
        String result = PlaceholderUtils.replacePlaceholders("${yesterday} ~ ${tomorrow}", Map.of());
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} ~ \\d{4}-\\d{2}-\\d{2}"), result);
    }
}
