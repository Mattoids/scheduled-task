package com.mattoid.scheduled.task;

import com.mattoid.scheduled.entity.DatasourceConfig;
import com.mattoid.scheduled.service.DatasourceConfigService;
import com.mattoid.scheduled.util.CryptoUtil;
import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SqlExecutor {

    private final DatasourceConfigService datasourceConfigService;

    public SqlExecutor(DatasourceConfigService datasourceConfigService) {
        this.datasourceConfigService = datasourceConfigService;
    }

    private static final Pattern SQL_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    public List<Map<String, Object>> executeQuery(Long datasourceId, String sql) throws Exception {
        return executeQuery(datasourceId, sql, java.util.Collections.emptyMap());
    }

    public List<Map<String, Object>> executeQuery(Long datasourceId, String sql, Map<String, Object> params) throws Exception {
        if (!org.springframework.util.StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("SQL 内容为空, datasourceId=" + datasourceId);
        }
        Map<String, Object> safeParams = params != null ? params : java.util.Collections.emptyMap();
        String processedSql = processSqlVariables(sql, safeParams);
        if (!processedSql.equals(sql)) {
            log.debug("SQL 变量替换后: {}", processedSql);
        }
        DataSource dataSource = datasourceConfigService.getDataSource(datasourceId);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(processedSql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                // 使用 LinkedHashMap 保持与 SQL 查询结果列一致的顺序
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    /**
     * 处理 SQL 中的 ${var} / ${var:format} 变量。
     * 支持的变量：
     *   month / currentMonth -> 当前月份（默认 M）
     *   lastMonth            -> 上月（默认 M）
     *   nextMonth            -> 下月（默认 M）
     *   lastM / nextM        -> 上月/下月数字（M）
     *   year / currentYear   -> 当前年份（默认 yyyy）
     *   lastYear             -> 去年（默认 yyyy）
     *   nextYear             -> 明年（默认 yyyy）
     *   now / date           -> 当前时间（默认 yyyy-MM-dd）
     *   today                -> 当前日期（默认 yyyy-MM-dd）
     *   firstDayOfThisWeek   -> 本周第一天（周一，默认 yyyy-MM-dd）
     *   lastDayOfThisWeek    -> 本周最后一天（周日，默认 yyyy-MM-dd）
     *   firstDayOfLastWeek   -> 上周第一天（周一，默认 yyyy-MM-dd）
     *   lastDayOfLastWeek    -> 上周最后一天（周日，默认 yyyy-MM-dd）
     *   firstDayOfThisMonth  -> 本月第一天（默认 yyyy-MM-dd）
     *   lastDayOfThisMonth   -> 本月最后一天（默认 yyyy-MM-dd）
     *   firstDayOfLastMonth  -> 上月第一天（默认 yyyy-MM-dd）
     *   lastDayOfLastMonth   -> 上月最后一天（默认 yyyy-MM-dd）
     *   firstDayOfThisYear   -> 今年第一天（默认 yyyy-MM-dd）
     *   lastDayOfThisYear    -> 今年最后一天（默认 yyyy-MM-dd）
     *   firstDayOfLastYear   -> 去年第一天（默认 yyyy-MM-dd）
     *   lastDayOfLastYear    -> 去年最后一天（默认 yyyy-MM-dd）
     *   firstDayOfNextYear   -> 明年第一天（默认 yyyy-MM-dd）
     *   lastDayOfNextYear    -> 明年最后一天（默认 yyyy-MM-dd）
     *   firstDayOfThisQuarter-> 本季度第一天（默认 yyyy-MM-dd）
     *   lastDayOfThisQuarter -> 本季度最后一天（默认 yyyy-MM-dd）
     *   firstDayOfLastQuarter-> 上季度第一天（默认 yyyy-MM-dd）
     *   lastDayOfLastQuarter -> 上季度最后一天（默认 yyyy-MM-dd）
     *   yesterday            -> 昨天（默认 yyyy-MM-dd）
     *   tomorrow             -> 明天（默认 yyyy-MM-dd）
     * 也可通过 ${var:format} 自定义格式，如 ${lastMonth:MM}、${year:yy}、${firstDayOfLastMonth:yyyy-MM-dd}。
     */
    String processSqlVariables(String sql, Map<String, Object> params) {
        if (sql == null || !sql.contains("${")) {
            return sql;
        }
        // 先替换自定义参数，再替换内置变量，避免内置变量名与参数值冲突
        String afterParams = replaceParamPlaceholders(sql, params);
        return replaceBuiltInPlaceholders(afterParams);
    }

    String processSqlVariables(String sql) {
        return processSqlVariables(sql, java.util.Collections.emptyMap());
    }

    private String replaceParamPlaceholders(String sql, Map<String, Object> params) {
        Matcher matcher = SQL_PLACEHOLDER_PATTERN.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = resolveParamPlaceholder(placeholder, params);
            if (replacement == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String resolveParamPlaceholder(String placeholder, Map<String, Object> params) {
        String variable;
        String format;
        int colonIndex = placeholder.indexOf(':');
        if (colonIndex >= 0) {
            variable = placeholder.substring(0, colonIndex);
            format = placeholder.substring(colonIndex + 1);
        } else {
            variable = placeholder;
            format = null;
        }
        if (!params.containsKey(variable)) {
            return null;
        }
        Object value = params.get(variable);
        if (value == null) {
            return null;
        }
        if (format != null && !format.isEmpty()) {
            String formatted = PlaceholderUtils.formatValue(value, format);
            if (formatted != null) {
                return formatted;
            }
        }
        String text = value.toString();
        if (value instanceof String || value instanceof Character) {
            return "'" + text.replace("'", "''") + "'";
        }
        return text;
    }

    private String replaceBuiltInPlaceholders(String sql) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();
        YearMonth lastMonth = currentMonth.minusMonths(1);
        YearMonth nextMonth = currentMonth.plusMonths(1);
        Year currentYear = Year.now();
        Year lastYear = currentYear.minusYears(1);
        Year nextYear = currentYear.plusYears(1);

        Matcher matcher = SQL_PLACEHOLDER_PATTERN.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = resolveBuiltInPlaceholder(placeholder, now, today, currentMonth, lastMonth, nextMonth, currentYear, lastYear, nextYear);
            if (replacement == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String resolveBuiltInPlaceholder(String placeholder,
                                             LocalDateTime now,
                                             LocalDate today,
                                             YearMonth currentMonth,
                                             YearMonth lastMonth,
                                             YearMonth nextMonth,
                                             Year currentYear,
                                             Year lastYear,
                                             Year nextYear) {
        String variable;
        String format;
        int colonIndex = placeholder.indexOf(':');
        if (colonIndex >= 0) {
            variable = placeholder.substring(0, colonIndex);
            format = placeholder.substring(colonIndex + 1);
        } else {
            variable = placeholder;
            format = null;
        }

        return switch (variable) {
            case "month", "currentMonth" -> format(currentMonth, format, "M");
            case "lastMonth" -> format(lastMonth, format, "M");
            case "nextMonth" -> format(nextMonth, format, "M");
            case "lastM" -> format(lastMonth, format, "M");
            case "nextM" -> format(nextMonth, format, "M");
            case "year", "currentYear" -> format(currentYear, format, "yyyy");
            case "lastYear" -> format(lastYear, format, "yyyy");
            case "nextYear" -> format(nextYear, format, "yyyy");
            case "now", "date" -> format(now, format, "yyyy-MM-dd");
            case "today" -> format(today, format, "yyyy-MM-dd");
            case "firstDayOfThisWeek", "lastDayOfThisWeek",
                    "firstDayOfLastWeek", "lastDayOfLastWeek",
                    "firstDayOfThisMonth", "lastDayOfThisMonth",
                    "firstDayOfLastMonth", "lastDayOfLastMonth",
                    "firstDayOfThisYear", "lastDayOfThisYear",
                    "firstDayOfLastYear", "lastDayOfLastYear",
                    "firstDayOfNextYear", "lastDayOfNextYear",
                    "firstDayOfThisQuarter", "lastDayOfThisQuarter",
                    "firstDayOfLastQuarter", "lastDayOfLastQuarter",
                    "yesterday", "tomorrow" -> {
                Object builtIn = PlaceholderUtils.resolveBuiltInVariable(variable);
                yield builtIn != null ? format(builtIn, format, "yyyy-MM-dd") : null;
            }
            default -> null;
        };
    }

    private String format(Object temporal, String format, String defaultFormat) {
        String pattern = (format == null || format.isEmpty()) ? defaultFormat : format;
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            if (temporal instanceof YearMonth ym) {
                return ym.format(formatter);
            } else if (temporal instanceof Year y) {
                return y.format(formatter);
            } else if (temporal instanceof LocalDateTime ldt) {
                return ldt.format(formatter);
            } else if (temporal instanceof LocalDate ld) {
                return ld.format(formatter);
            }
        } catch (Exception e) {
            log.warn("SQL 变量格式 '{}' 不合法: {}", pattern, e.getMessage());
        }
        return null;
    }
}
