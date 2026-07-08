package com.mattoid.scheduled.task;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlExecutorTest {

    private final SqlExecutor executor = new SqlExecutor(null);

    @Test
    void replacesLastMonthVariables() {
        String sql = "SELECT '${lastM}月' AS month_label, '${lastMonth:MM}' AS padded";
        String result = executor.processSqlVariables(sql);
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        String expected = "SELECT '" + lastMonth.format(DateTimeFormatter.ofPattern("M")) + "月' AS month_label, '"
                + lastMonth.format(DateTimeFormatter.ofPattern("MM")) + "' AS padded";
        assertEquals(expected, result);
    }

    @Test
    void replacesCurrentMonthVariables() {
        String sql = "SELECT '${month}月' AS label, '${currentMonth:MM}' AS padded";
        String result = executor.processSqlVariables(sql);
        YearMonth current = YearMonth.now();
        String expected = "SELECT '" + current.format(DateTimeFormatter.ofPattern("M")) + "月' AS label, '"
                + current.format(DateTimeFormatter.ofPattern("MM")) + "' AS padded";
        assertEquals(expected, result);
    }

    @Test
    void replacesYearVariables() {
        String sql = "SELECT ${year} AS y, ${lastYear:yy} AS ly, ${nextYear:yyyy} AS ny";
        String result = executor.processSqlVariables(sql);
        int year = LocalDate.now().getYear();
        int lastYear = year - 1;
        int nextYear = year + 1;
        String expected = "SELECT " + year + " AS y, " + String.valueOf(lastYear % 100) + " AS ly, " + nextYear + " AS ny";
        assertEquals(expected, result);
    }

    @Test
    void keepsUnknownPlaceholders() {
        String sql = "SELECT ${unknown} AS x, ${city} AS y";
        String result = executor.processSqlVariables(sql);
        assertEquals(sql, result);
    }

    @Test
    void leavesSqlWithoutPlaceholdersUnchanged() {
        String sql = "SELECT 1";
        assertEquals(sql, executor.processSqlVariables(sql));
    }

    @Test
    void supportsUserExample() {
        String sql = "SELECT city_name AS '城市', checkin_num AS '${lastM}月打卡门店次数' FROM t";
        String result = executor.processSqlVariables(sql);
        String month = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("M"));
        assertEquals("SELECT city_name AS '城市', checkin_num AS '" + month + "月打卡门店次数' FROM t", result);
    }

    @Test
    void formatsDateParamWithPattern() {
        String sql = "SELECT * FROM t WHERE date >= '${startTime:yyyy-MM-dd}'";
        Map<String, Object> params = Map.of("startTime", LocalDateTime.of(2026, 7, 7, 10, 30, 0));
        String result = executor.processSqlVariables(sql, params);
        assertEquals("SELECT * FROM t WHERE date >= '2026-07-07'", result);
    }

    @Test
    void formatsStringDateParamWithPattern() {
        String sql = "SELECT * FROM t WHERE month = '${startTime:yyyy-MM}'";
        Map<String, Object> params = Map.of("startTime", "2026-07-07");
        String result = executor.processSqlVariables(sql, params);
        assertEquals("SELECT * FROM t WHERE month = '2026-07'", result);
    }

    @Test
    void fallsBackToPlainStringWhenFormatInvalid() {
        String sql = "SELECT * FROM t WHERE id = '${code:abc}'";
        Map<String, Object> params = Map.of("code", "XYZ");
        String result = executor.processSqlVariables(sql, params);
        assertEquals("SELECT * FROM t WHERE id = 'XYZ'", result);
    }

    @Test
    void replacesFirstDayOfLastMonth() {
        String sql = "SELECT * FROM t WHERE date >= '${firstDayOfLastMonth}' AND date <= '${lastDayOfLastMonth:yyyy/MM/dd}'";
        String result = executor.processSqlVariables(sql);
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastDay = today.minusMonths(1).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        assertTrue(result.contains("'" + firstDay + "'"), result);
        assertTrue(result.contains("'" + lastDay.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "'"), result);
    }

    @Test
    void replacesYearBoundaryVariables() {
        String sql = "SELECT * FROM t WHERE date >= '${firstDayOfThisYear}' AND date <= '${lastDayOfThisYear}'";
        String result = executor.processSqlVariables(sql);
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.with(java.time.temporal.TemporalAdjusters.firstDayOfYear());
        LocalDate lastDay = today.with(java.time.temporal.TemporalAdjusters.lastDayOfYear());
        assertTrue(result.contains("'" + firstDay + "'"), result);
        assertTrue(result.contains("'" + lastDay + "'"), result);
    }

    @Test
    void replacesYesterdayAndTomorrow() {
        String sql = "SELECT * FROM t WHERE date >= '${yesterday}' AND date <= '${tomorrow:yyyy/MM/dd}'";
        String result = executor.processSqlVariables(sql);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);
        assertTrue(result.contains("'" + yesterday + "'"), result);
        assertTrue(result.contains("'" + tomorrow.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "'"), result);
    }

    @Test
    void customParamsTakePrecedenceOverBuiltInVariables() {
        String sql = "SELECT * FROM t WHERE month = '${lastMonth}'";
        Map<String, Object> params = Map.of("lastMonth", "custom-value");
        String result = executor.processSqlVariables(sql, params);
        assertEquals("SELECT * FROM t WHERE month = 'custom-value'", result);
    }
}
