package com.mattoid.scheduled.task;

import com.mattoid.scheduled.service.DatasourceConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class SqlExecutor {

    private final DatasourceConfigService datasourceConfigService;

    public SqlExecutor(DatasourceConfigService datasourceConfigService) {
        this.datasourceConfigService = datasourceConfigService;
    }

    private static final Pattern SQL_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern SQL_COMMENT_PATTERN = Pattern.compile("(?s)/\\*.*?\\*/|--[^\\r\\n]*");
    private static final Pattern SQL_NON_READ_ONLY_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|REPLACE|GRANT|REVOKE|EXEC|EXECUTE|CALL|LOAD)\\b",
            Pattern.CASE_INSENSITIVE);

    public static void validateReadOnlySql(String sql) {
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("SQL 内容为空");
        }
        String stripped = SQL_COMMENT_PATTERN.matcher(sql).replaceAll(" ");
        String trimmed = stripped.trim();
        if (!trimmed.regionMatches(true, 0, "SELECT", 0, 6)) {
            throw new IllegalArgumentException("AI 对话仅允许执行 SELECT 查询语句");
        }
        if (SQL_NON_READ_ONLY_PATTERN.matcher(trimmed).find()) {
            throw new IllegalArgumentException("AI 对话禁止执行包含 INSERT/UPDATE/DELETE/DROP/ALTER 等变更关键字的 SQL");
        }
    }

    public List<Map<String, Object>> executeQuery(Long datasourceId, String sql) throws Exception {
        return executeQuery(datasourceId, sql, Collections.emptyMap());
    }

    /**
     * 预览 SQL：将变量填充为具体值并返回完整 SQL 文本，不连接数据库执行。
     */
    public String previewSql(String sql, Map<String, Object> params) {
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("SQL 内容为空");
        }
        Map<String, Object> safeParams = params != null ? params : Collections.emptyMap();
        SqlWithParameters processed = processSqlVariables(sql, safeParams);
        return reconstructFullSql(processed.sql(), processed.parameters());
    }

    public List<Map<String, Object>> executeQuery(Long datasourceId, String sql, Map<String, Object> params) throws Exception {
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("SQL 内容为空, datasourceId=" + datasourceId);
        }
        Map<String, Object> safeParams = params != null ? params : Collections.emptyMap();
        SqlWithParameters processed = processSqlVariables(sql, safeParams);
        String fullSql = reconstructFullSql(processed.sql(), processed.parameters());
        log.info("执行查询 SQL: datasourceId={}, sql={}", datasourceId, fullSql);
        if (!processed.sql().equals(sql)) {
            log.debug("SQL 变量替换前: {}", sql);
        }
        DataSource dataSource = datasourceConfigService.getDataSource(datasourceId);
        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);
            try (PreparedStatement stmt = conn.prepareStatement(processed.sql())) {
                List<Object> parameters = processed.parameters();
                for (int i = 0; i < parameters.size(); i++) {
                    stmt.setObject(i + 1, parameters.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
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
        }
    }

    /**
     * 处理 SQL 中的 ${var} / ${var:format} 变量，将其转换为 PreparedStatement 参数占位符。
     * 所有可解析的变量（自定义参数与内置日期变量）均替换为 ?，对应的值按顺序存入 parameters。
     * 无法解析的占位符保持原样，最终通过 PreparedStatement 设置参数，避免字符串拼接导致 SQL 注入。
     *
     * 支持的变量详见 {@link #resolvePlaceholder(String, Map)}。
     */
    SqlWithParameters processSqlVariables(String sql, Map<String, Object> params) {
        if (sql == null || !sql.contains("${")) {
            return new SqlWithParameters(sql, Collections.emptyList());
        }
        List<Object> parameters = new ArrayList<>();
        String processedSql = replacePlaceholders(sql, params, parameters);
        return new SqlWithParameters(processedSql, Collections.unmodifiableList(parameters));
    }

    SqlWithParameters processSqlVariables(String sql) {
        return processSqlVariables(sql, Collections.emptyMap());
    }

    private String replacePlaceholders(String sql, Map<String, Object> params, List<Object> parameters) {
        Matcher matcher = SQL_PLACEHOLDER_PATTERN.matcher(sql);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(sql, lastEnd, matcher.start());
            String placeholder = matcher.group(1);
            Object value = resolvePlaceholder(placeholder, params);
            if (value == null) {
                sb.append(matcher.group(0));
                lastEnd = matcher.end();
            } else {
                parameters.add(value);
                boolean quoted = isSingleQuoted(sql, matcher.start(), matcher.end());
                if (quoted) {
                    // 移除已追加到 sb 中的前导单引号，避免生成 ''?'' 类语法错误
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\'') {
                        sb.setLength(sb.length() - 1);
                    }
                    lastEnd = matcher.end() + 1;
                } else {
                    lastEnd = matcher.end();
                }
                sb.append('?');
            }
        }
        sb.append(sql, lastEnd, sql.length());
        return sb.toString();
    }

    /**
     * 递归解析字符串中的 ${占位符}，直到所有占位符都被解析为非占位符值。
     * 返回不带单引号包裹的裸值（引号由外层 resolvePlaceholder / replacePlaceholders 统一处理）。
     */
    private String resolveNestedPlaceholder(String value, Map<String, Object> params) {
        Matcher matcher = SQL_PLACEHOLDER_PATTERN.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        matcher.reset();
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String inner = matcher.group(1);
            String innerVar = inner.contains(":") ? inner.substring(0, inner.indexOf(':')) : inner;
            String innerFormat = inner.contains(":") ? inner.substring(inner.indexOf(':') + 1) : null;
            Object resolved = params.containsKey(innerVar) ? params.get(innerVar) : resolveBuiltInVariable(innerVar);
            if (resolved == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }
            // 如果解析出来的值本身还是占位符，递归解析
            if (resolved instanceof String s && s.contains("${")) {
                resolved = resolveNestedPlaceholder(s, params);
            }
            if (resolved != null && innerFormat != null && !innerFormat.isEmpty()) {
                resolved = formatValue(resolved, innerFormat);
            }
            String replacement;
            if (resolved == null) {
                replacement = matcher.group(0);
            } else if (resolved instanceof Number) {
                replacement = resolved.toString();
            } else {
                replacement = "'" + resolved.toString().replace("'", "''") + "'";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        String result = sb.toString();
        // 剥掉首尾的单引号包裹（引号统一由 replacePlaceholders 处理）
        if (result.length() >= 2 && result.startsWith("'") && result.endsWith("'")) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private boolean isSingleQuoted(String sql, int start, int end) {
        return start > 0 && end < sql.length() && sql.charAt(start - 1) == '\'' && sql.charAt(end) == '\'';
    }

    /**
     * 将带 ? 占位符的 SQL 和参数列表拼接回完整 SQL，用于日志输出。
     */
    private String reconstructFullSql(String sqlWithPlaceholders, List<Object> params) {
        StringBuilder sb = new StringBuilder();
        int paramIndex = 0;
        for (int i = 0; i < sqlWithPlaceholders.length(); i++) {
            char c = sqlWithPlaceholders.charAt(i);
            if (c == '?' && paramIndex < params.size()) {
                Object val = params.get(paramIndex++);
                sb.append(formatParam(val));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String formatParam(Object val) {
        if (val == null) {
            return "NULL";
        }
        if (val instanceof Number) {
            return val.toString();
        }
        return "'" + val.toString().replace("'", "''") + "'";
    }

    private Object resolvePlaceholder(String placeholder, Map<String, Object> params) {
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

        // 自定义参数优先级高于内置变量，与历史行为保持一致
        if (params.containsKey(variable)) {
            Object value = params.get(variable);
            if (value == null) {
                return null;
            }
            // 如果参数值本身还是占位符，递归解析（例如 ${startTime} = "${firstDayOfLastMonth}"）
            if (value instanceof String str && str.contains("${")) {
                value = resolveNestedPlaceholder(str, params);
            }
            if (format != null && !format.isEmpty()) {
                String formatted = formatValue(value, format);
                if (formatted != null) {
                    return formatted;
                }
            }
            return value;
        }

        Object builtIn = resolveBuiltInVariable(variable);
        if (builtIn != null) {
            String pattern = (format == null || format.isEmpty()) ? defaultFormat(builtIn) : format;
            if (pattern == null) {
                return String.valueOf(builtIn);
            }
            String formatted = formatValue(builtIn, pattern);
            return formatted != null ? formatted : String.valueOf(builtIn);
        }
        return null;
    }

    private Object resolveBuiltInVariable(String variable) {
        return switch (variable) {
            case "month", "currentMonth" -> YearMonth.now();
            case "lastMonth", "lastM" -> YearMonth.now().minusMonths(1);
            case "nextMonth", "nextM" -> YearMonth.now().plusMonths(1);
            case "year", "currentYear" -> Year.now();
            case "lastYear" -> Year.now().minusYears(1);
            case "nextYear" -> Year.now().plusYears(1);
            case "now", "date" -> LocalDateTime.now();
            case "today" -> LocalDate.now();
            case "firstDayOfThisWeek", "lastDayOfThisWeek",
                    "firstDayOfLastWeek", "lastDayOfLastWeek",
                    "firstDayOfThisMonth", "lastDayOfThisMonth",
                    "firstDayOfLastMonth", "lastDayOfLastMonth",
                    "firstDayOfThisYear", "lastDayOfThisYear",
                    "firstDayOfLastYear", "lastDayOfLastYear",
                    "firstDayOfNextYear", "lastDayOfNextYear",
                    "firstDayOfThisQuarter", "lastDayOfThisQuarter",
                    "firstDayOfLastQuarter", "lastDayOfLastQuarter",
                    "yesterday", "tomorrow" -> com.mattoid.scheduled.util.PlaceholderUtils.resolveBuiltInVariable(variable);
            default -> null;
        };
    }

    private String defaultFormat(Object value) {
        if (value instanceof LocalDateTime || value instanceof LocalDate) {
            return "yyyy-MM-dd";
        } else if (value instanceof YearMonth) {
            return "M";
        } else if (value instanceof Year) {
            return "yyyy";
        }
        return null;
    }

    private String formatValue(Object value, String pattern) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            if (value instanceof YearMonth ym) {
                return ym.format(formatter);
            } else if (value instanceof Year y) {
                return y.format(formatter);
            } else if (value instanceof LocalDateTime ldt) {
                return ldt.format(formatter);
            } else if (value instanceof LocalDate ld) {
                return ld.format(formatter);
            } else if (value instanceof String str) {
                return formatStringDate(str, formatter);
            }
            return null;
        } catch (Exception e) {
            log.warn("SQL 变量格式 '{}' 不合法: {}", pattern, e.getMessage());
        }
        return null;
    }

    private String formatStringDate(String value, DateTimeFormatter formatter) {
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

    record SqlWithParameters(String sql, List<Object> parameters) {
    }
}
