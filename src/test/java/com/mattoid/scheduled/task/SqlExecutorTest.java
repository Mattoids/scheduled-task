package com.mattoid.scheduled.task;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlExecutorTest {

    private final SqlExecutor executor = new SqlExecutor(null);

    @Test
    void replacesLastMonthVariables() {
        String sql = "SELECT '${lastM}月' AS month_label, '${lastMonth:MM}' AS padded";
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql);
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        String expected = "SELECT '?月' AS month_label, ? AS padded";
        assertEquals(expected, result.sql());
        assertEquals(2, result.parameters().size());
        assertEquals(lastMonth.format(DateTimeFormatter.ofPattern("M")), result.parameters().get(0));
        assertEquals(lastMonth.format(DateTimeFormatter.ofPattern("MM")), result.parameters().get(1));
    }

    @Test
    void replacesCurrentMonthVariables() {
        String sql = "SELECT '${month}月' AS label, '${currentMonth:MM}' AS padded";
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql);
        YearMonth current = YearMonth.now();
        assertEquals("SELECT '?月' AS label, ? AS padded", result.sql());
        assertEquals(2, result.parameters().size());
        assertEquals(current.format(DateTimeFormatter.ofPattern("M")), result.parameters().get(0));
        assertEquals(current.format(DateTimeFormatter.ofPattern("MM")), result.parameters().get(1));
    }

    @Test
    void replacesYearVariables() {
        String sql = "SELECT ${year} AS y, ${lastYear:yy} AS ly, ${nextYear:yyyy} AS ny";
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql);
        int year = LocalDate.now().getYear();
        int lastYear = year - 1;
        int nextYear = year + 1;
        assertEquals("SELECT ? AS y, ? AS ly, ? AS ny", result.sql());
        assertEquals(String.valueOf(year), result.parameters().get(0));
        assertEquals(String.valueOf(lastYear % 100), result.parameters().get(1));
        assertEquals(String.valueOf(nextYear), result.parameters().get(2));
    }

    @Test
    void keepsUnknownPlaceholders() {
        String sql = "SELECT ${unknown} AS x, ${city} AS y";
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql);
        assertEquals(sql, result.sql());
        assertTrue(result.parameters().isEmpty());
    }

    @Test
    void leavesSqlWithoutPlaceholdersUnchanged() {
        String sql = "SELECT 1";
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql);
        assertEquals(sql, result.sql());
        assertTrue(result.parameters().isEmpty());
    }

    @Test
    void supportsUserExample() {
        String sql = "SELECT city_name AS '城市', checkin_num AS '${lastM}月打卡门店次数' FROM t";
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql);
        String month = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("M"));
        assertEquals("SELECT city_name AS '城市', checkin_num AS '?月打卡门店次数' FROM t", result.sql());
        assertEquals(1, result.parameters().size());
        assertEquals(month, result.parameters().get(0));
    }

    @Test
    void formatsDateParamWithPattern() {
        String sql = "SELECT * FROM t WHERE date >= '${startTime:yyyy-MM-dd}'";
        Map<String, Object> params = Map.of("startTime", LocalDateTime.of(2026, 7, 7, 10, 30, 0));
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql, params);
        assertEquals("SELECT * FROM t WHERE date >= ?", result.sql());
        assertEquals(1, result.parameters().size());
        assertEquals("2026-07-07", result.parameters().get(0));
    }

    @Test
    void formatsStringDateParamWithPattern() {
        String sql = "SELECT * FROM t WHERE month = '${startTime:yyyy-MM}'";
        Map<String, Object> params = Map.of("startTime", "2026-07-07");
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql, params);
        assertEquals("SELECT * FROM t WHERE month = ?", result.sql());
        assertEquals("2026-07", result.parameters().get(0));
    }

    @Test
    void fallsBackToPlainStringWhenFormatInvalid() {
        String sql = "SELECT * FROM t WHERE id = '${code:abc}'";
        Map<String, Object> params = Map.of("code", "XYZ");
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql, params);
        assertEquals("SELECT * FROM t WHERE id = ?", result.sql());
        assertEquals("XYZ", result.parameters().get(0));
    }

    @Test
    void replacesFirstDayOfLastMonth() {
        String sql = "SELECT * FROM t WHERE date >= '${firstDayOfLastMonth}' AND date <= '${lastDayOfLastMonth:yyyy/MM/dd}'";
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql);
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.minusMonths(1).withDayOfMonth(1);
        LocalDate lastDay = today.minusMonths(1).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        assertEquals("SELECT * FROM t WHERE date >= ? AND date <= ?", result.sql());
        assertEquals(firstDay.toString(), result.parameters().get(0));
        assertEquals(lastDay.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), result.parameters().get(1));
    }

    @Test
    void replacesYearBoundaryVariables() {
        String sql = "SELECT * FROM t WHERE date >= '${firstDayOfThisYear}' AND date <= '${lastDayOfThisYear}'";
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql);
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.with(java.time.temporal.TemporalAdjusters.firstDayOfYear());
        LocalDate lastDay = today.with(java.time.temporal.TemporalAdjusters.lastDayOfYear());
        assertEquals("SELECT * FROM t WHERE date >= ? AND date <= ?", result.sql());
        assertEquals(firstDay.toString(), result.parameters().get(0));
        assertEquals(lastDay.toString(), result.parameters().get(1));
    }

    @Test
    void replacesYesterdayAndTomorrow() {
        String sql = "SELECT * FROM t WHERE date >= '${yesterday}' AND date <= '${tomorrow:yyyy/MM/dd}'";
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);
        assertEquals("SELECT * FROM t WHERE date >= ? AND date <= ?", result.sql());
        assertEquals(yesterday.toString(), result.parameters().get(0));
        assertEquals(tomorrow.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")), result.parameters().get(1));
    }

    @Test
    void customParamsTakePrecedenceOverBuiltInVariables() {
        String sql = "SELECT * FROM t WHERE month = '${lastMonth}'";
        Map<String, Object> params = Map.of("lastMonth", "custom-value");
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql, params);
        assertEquals("SELECT * FROM t WHERE month = ?", result.sql());
        assertEquals("custom-value", result.parameters().get(0));
    }

    @Test
    void parameterizationPreventsSqlInjectionInQuotedPlaceholder() {
        String sql = "SELECT * FROM users WHERE name = '${name}'";
        Map<String, Object> params = Map.of("name", "admin' OR '1'='1");
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql, params);
        assertEquals("SELECT * FROM users WHERE name = ?", result.sql());
        assertEquals("admin' OR '1'='1", result.parameters().get(0));
    }

    @Test
    void parameterizationPreventsSqlInjectionInUnquotedPlaceholder() {
        String sql = "SELECT * FROM users WHERE id = ${id}";
        Map<String, Object> params = Map.of("id", "1; DROP TABLE users; --");
        SqlExecutor.SqlWithParameters result = executor.processSqlVariables(sql, params);
        assertEquals("SELECT * FROM users WHERE id = ?", result.sql());
        assertEquals("1; DROP TABLE users; --", result.parameters().get(0));
    }

    @Test
    void previewSqlFillsCustomParamsAndBuiltInVariables() {
        String sql = "SELECT * FROM t WHERE date >= '${startTime}' AND date <= '${endTime}' AND month = '${lastMonth:MM}'";
        Map<String, Object> params = Map.of(
                "startTime", "2026-07-01 00:00:00",
                "endTime", "2026-07-31 23:59:59"
        );
        String previewSql = executor.previewSql(sql, params);
        String expectedLastMonth = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("MM"));
        assertEquals("SELECT * FROM t WHERE date >= '2026-07-01 00:00:00' AND date <= '2026-07-31 23:59:59' AND month = '" + expectedLastMonth + "'", previewSql);
    }

    @Test
    void previewSqlKeepsUnknownPlaceholders() {
        String sql = "SELECT * FROM t WHERE id = ${unknown}";
        String previewSql = executor.previewSql(sql, Collections.emptyMap());
        assertEquals(sql, previewSql);
    }

    @Test
    void previewSqlThrowsWhenSqlContentEmpty() {
        try {
            executor.previewSql("", Collections.emptyMap());
            org.junit.jupiter.api.Assertions.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("SQL 内容为空", e.getMessage());
        }
    }
}
