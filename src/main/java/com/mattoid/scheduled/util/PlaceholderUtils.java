package com.mattoid.scheduled.util;

import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderUtils {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    public static String replacePlaceholders(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return pattern;
        }
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
        return sb.toString();
    }
}
