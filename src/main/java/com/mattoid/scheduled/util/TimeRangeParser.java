package com.mattoid.scheduled.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从自然语言中提取常见时间范围。
 * 返回的 Map 中固定包含 startTime 和 endTime，格式为 yyyy-MM-dd HH:mm:ss。
 */
public class TimeRangeParser {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static Map<String, String> parse(String input) {
        Map<String, String> result = new HashMap<>();
        if (input == null || input.isEmpty()) {
            return result;
        }
        String text = input.toLowerCase();

        LocalDate today = LocalDate.now();

        // 去年 / 上一年
        if (containsAny(text, "去年", "上一年", "上年")) {
            LocalDate start = today.minusYears(1).withDayOfYear(1);
            LocalDate end = today.minusYears(1).withDayOfYear(today.minusYears(1).lengthOfYear());
            putRange(result, start.atStartOfDay(), end.atTime(LocalTime.MAX));
            return result;
        }
        // 今年 / 本年 / 当年
        if (containsAny(text, "今年", "本年", "当年")) {
            LocalDate start = today.withDayOfYear(1);
            LocalDate end = today.withDayOfYear(today.lengthOfYear());
            putRange(result, start.atStartOfDay(), end.atTime(LocalTime.MAX));
            return result;
        }
        // 上个月 / 上月
        if (containsAny(text, "上个月", "上月")) {
            YearMonth lastMonth = YearMonth.now().minusMonths(1);
            putRange(result, lastMonth.atDay(1).atStartOfDay(), lastMonth.atEndOfMonth().atTime(LocalTime.MAX));
            return result;
        }
        // 本月 / 这个月
        if (containsAny(text, "本月", "这个月")) {
            YearMonth currentMonth = YearMonth.now();
            putRange(result, currentMonth.atDay(1).atStartOfDay(), currentMonth.atEndOfMonth().atTime(LocalTime.MAX));
            return result;
        }
        // 上季度
        if (containsAny(text, "上季度", "上个季度", "上一季度")) {
            LocalDate start = today.minusMonths(3).with(IsoFields.DAY_OF_QUARTER, 1);
            LocalDate end = start.plusMonths(3).minusDays(1);
            putRange(result, start.atStartOfDay(), end.atTime(LocalTime.MAX));
            return result;
        }
        // 本季度
        if (containsAny(text, "本季度", "这个季度", "本季")) {
            LocalDate start = today.with(IsoFields.DAY_OF_QUARTER, 1);
            LocalDate end = start.plusMonths(3).minusDays(1);
            putRange(result, start.atStartOfDay(), end.atTime(LocalTime.MAX));
            return result;
        }
        // 上周 / 上星期
        if (containsAny(text, "上周", "上星期", "上个星期")) {
            LocalDate start = today.minusWeeks(1).with(DayOfWeek.MONDAY);
            LocalDate end = start.plusDays(6);
            putRange(result, start.atStartOfDay(), end.atTime(LocalTime.MAX));
            return result;
        }
        // 本周 / 这星期
        if (containsAny(text, "本周", "这周", "本星期", "这个星期")) {
            LocalDate start = today.with(DayOfWeek.MONDAY);
            LocalDate end = start.plusDays(6);
            putRange(result, start.atStartOfDay(), end.atTime(LocalTime.MAX));
            return result;
        }
        // 昨天
        if (containsAny(text, "昨天", "昨日")) {
            LocalDate yesterday = today.minusDays(1);
            putRange(result, yesterday.atStartOfDay(), yesterday.atTime(LocalTime.MAX));
            return result;
        }
        // 今天
        if (containsAny(text, "今天", "今日", "当天")) {
            putRange(result, today.atStartOfDay(), today.atTime(LocalTime.MAX));
            return result;
        }
        // 最近 N 天 / 过去 N 天
        Matcher dayMatcher = Pattern.compile("(?:最近|过去|近)\\s*([0-9]+)\\s*天").matcher(text);
        if (dayMatcher.find()) {
            int days = Integer.parseInt(dayMatcher.group(1));
            LocalDate start = today.minusDays(days - 1L);
            putRange(result, start.atStartOfDay(), today.atTime(LocalTime.MAX));
            return result;
        }
        // 最近 N 小时
        Matcher hourMatcher = Pattern.compile("(?:最近|过去|近)\\s*([0-9]+)\\s*(?:小时|个钟头)").matcher(text);
        if (hourMatcher.find()) {
            int hours = Integer.parseInt(hourMatcher.group(1));
            LocalDateTime start = LocalDateTime.now().minusHours(hours);
            putRange(result, start, LocalDateTime.now());
            return result;
        }

        return result;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static void putRange(Map<String, String> result, LocalDateTime start, LocalDateTime end) {
        result.put("startTime", start.format(FORMATTER));
        result.put("endTime", end.format(FORMATTER));
    }
}
