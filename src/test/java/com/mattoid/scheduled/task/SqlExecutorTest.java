package com.mattoid.scheduled.task;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

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
}
