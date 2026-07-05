package com.mattoid.scheduled.util;

import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
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

        // Replace {xx} date/time placeholders
        LocalDateTime now = LocalDateTime.now();
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(pattern);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement;
            if ("lastMonth".equals(placeholder)) {
                replacement = lastMonth.format(DateTimeFormatter.ofPattern("MM"));
            } else if (placeholder.startsWith("lastMonth:")) {
                String format = placeholder.substring("lastMonth:".length());
                replacement = lastMonth.format(DateTimeFormatter.ofPattern(format));
            } else if ("nextMonth".equals(placeholder)) {
                replacement = nextMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));
            } else if (placeholder.startsWith("nextMonth:")) {
                String format = placeholder.substring("nextMonth:".length());
                replacement = nextMonth.format(DateTimeFormatter.ofPattern(format));
            } else {
                try {
                    replacement = now.format(DateTimeFormatter.ofPattern(placeholder));
                } catch (IllegalArgumentException e) {
                    replacement = matcher.group(0);
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        String result = sb.toString();

        // Replace ${xx} data placeholders
        Matcher dollarMatcher = DOLLAR_PLACEHOLDER_PATTERN.matcher(result);
        StringBuffer dollarSb = new StringBuffer();
        while (dollarMatcher.find()) {
            String key = dollarMatcher.group(1);
            Object value = resolveValue(data, key);
            String replacement = value != null ? String.valueOf(value) : dollarMatcher.group(0);
            dollarMatcher.appendReplacement(dollarSb, Matcher.quoteReplacement(replacement));
        }
        dollarMatcher.appendTail(dollarSb);
        return dollarSb.toString();
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
