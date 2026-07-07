package com.mattoid.scheduled.util;

import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderUtils {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final Pattern DOLLAR_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    public static String replacePlaceholders(String pattern) {
        return replacePlaceholders(pattern, null);
    }

    public static String replacePlaceholders(String pattern, Map<String, Object> data) {
        if (!StringUtils.hasText(pattern)) {
            return pattern;
        }
        if (data == null) {
            data = Collections.emptyMap();
        }

        // Replace ${xx} data / built-in placeholders first, so ${firstDayOfLastMonth} is not eaten by {xx} regex
        Matcher dollarMatcher = DOLLAR_PLACEHOLDER_PATTERN.matcher(pattern);
        StringBuffer dollarSb = new StringBuffer();
        while (dollarMatcher.find()) {
            String content = dollarMatcher.group(1);
            String key;
            String format;
            int colonIdx = content.indexOf(':');
            if (colonIdx > 0) {
                key = content.substring(0, colonIdx);
                format = content.substring(colonIdx + 1);
            } else {
                key = content;
                format = null;
            }
            Object value = resolveValue(data, key);
            String replacement;
            if (value != null) {
                if (StringUtils.hasText(format)) {
                    String formatted = formatValue(value, format);
                    replacement = formatted != null ? formatted : String.valueOf(value);
                } else {
                    replacement = String.valueOf(value);
                }
            } else {
                Object builtIn = resolveBuiltInVariable(key);
                if (builtIn != null) {
                    replacement = formatBuiltInVariable(builtIn, format);
                } else {
                    replacement = dollarMatcher.group(0);
                }
            }
            dollarMatcher.appendReplacement(dollarSb, Matcher.quoteReplacement(replacement));
        }
        dollarMatcher.appendTail(dollarSb);
        String result = dollarSb.toString();

        // Replace {xx} date/time placeholders
        LocalDateTime now = LocalDateTime.now();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = resolveCurlyPlaceholder(placeholder, now);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolveCurlyPlaceholder(String placeholder, LocalDateTime now) {
        String variable;
        String format;
        int colonIdx = placeholder.indexOf(':');
        if (colonIdx >= 0) {
            variable = placeholder.substring(0, colonIdx);
            format = placeholder.substring(colonIdx + 1);
        } else {
            variable = placeholder;
            format = null;
        }

        // 保持历史默认格式兼容
        if ("lastMonth".equals(variable) && format == null) {
            format = "MM";
        } else if ("nextMonth".equals(variable) && format == null) {
            format = "yyyyMM";
        }

        Object value = resolveBuiltInVariable(variable);
        if (value != null) {
            return formatBuiltInVariable(value, format);
        }

        try {
            return now.format(DateTimeFormatter.ofPattern(placeholder));
        } catch (IllegalArgumentException e) {
            return "{" + placeholder + "}";
        }
    }

    /**
     * 解析内置日期/时间变量。
     */
    public static Object resolveBuiltInVariable(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        return switch (name) {
            case "month", "currentMonth" -> YearMonth.now();
            case "lastMonth" -> YearMonth.now().minusMonths(1);
            case "nextMonth" -> YearMonth.now().plusMonths(1);
            case "year", "currentYear" -> Year.now();
            case "lastYear" -> Year.now().minusYears(1);
            case "nextYear" -> Year.now().plusYears(1);
            case "now", "date" -> LocalDateTime.now();
            case "today" -> LocalDate.now();
            case "firstDayOfThisWeek" -> today.with(DayOfWeek.MONDAY);
            case "lastDayOfThisWeek" -> today.with(DayOfWeek.SUNDAY);
            case "firstDayOfLastWeek" -> today.minusWeeks(1).with(DayOfWeek.MONDAY);
            case "lastDayOfLastWeek" -> today.minusWeeks(1).with(DayOfWeek.SUNDAY);
            case "firstDayOfThisMonth" -> today.withDayOfMonth(1);
            case "lastDayOfThisMonth" -> today.with(TemporalAdjusters.lastDayOfMonth());
            case "firstDayOfLastMonth" -> today.minusMonths(1).withDayOfMonth(1);
            case "lastDayOfLastMonth" -> today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
            default -> null;
        };
    }

    private static String formatBuiltInVariable(Object value, String format) {
        if (value == null) {
            return "";
        }
        String defaultFormat;
        if (value instanceof LocalDateTime || value instanceof LocalDate) {
            defaultFormat = "yyyy-MM-dd";
        } else if (value instanceof YearMonth) {
            defaultFormat = "yyyy-MM";
        } else if (value instanceof Year) {
            defaultFormat = "yyyy";
        } else {
            defaultFormat = null;
        }
        String pattern = StringUtils.hasText(format) ? format : defaultFormat;
        if (pattern == null) {
            return String.valueOf(value);
        }
        String formatted = formatValue(value, pattern);
        return formatted != null ? formatted : String.valueOf(value);
    }

    public static String formatValue(Object value, String format) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            if (value instanceof TemporalAccessor temporal) {
                return formatter.format(temporal);
            }
            if (value instanceof Date date) {
                return formatter.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
            if (value instanceof Instant instant) {
                return formatter.format(instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
            }
            if (value instanceof String str) {
                return formatStringDate(str, formatter);
            }
        } catch (IllegalArgumentException e) {
            // format is not a valid date pattern, fall back to plain string
        }
        return String.valueOf(value);
    }

    private static String formatStringDate(String value, DateTimeFormatter formatter) {
        // Try common ISO date/time formats
        try {
            return formatter.format(LocalDateTime.parse(value));
        } catch (Exception ignored) {
        }
        try {
            return formatter.format(LocalDate.parse(value));
        } catch (Exception ignored) {
        }
        try {
            return formatter.format(YearMonth.parse(value));
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object resolveValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value != null) {
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                if (list.isEmpty()) {
                    return "";
                }
                if (list.size() == 1) {
                    return list.get(0);
                }
                return String.join("；", list.stream().map(Object::toString).toArray(String[]::new));
            }
            return value;
        }
        // Fallback: try dotted notation like sqlName.colName
        int dotIdx = key.indexOf('.');
        if (dotIdx > 0) {
            String prefix = key.substring(0, dotIdx);
            String subKey = key.substring(dotIdx + 1);
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (entry.getKey().startsWith(prefix + ".")) {
                    String resolvedKey = entry.getKey().substring(prefix.length() + 1);
                    if (subKey.equals(resolvedKey) || entry.getKey().equals(key)) {
                        Object v = entry.getValue();
                        if (v instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Object> list = (List<Object>) v;
                            return list.isEmpty() ? "" : String.join("；", list.stream().map(Object::toString).toArray(String[]::new));
                        }
                        return v;
                    }
                }
            }
        }
        return null;
    }
}
