package com.mattoid.scheduled.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.task.SqlExecutor;
import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ExcelLoopHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private final SqlExecutor sqlExecutor;

    public ExcelLoopHelper(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    public boolean isLoopEnabled(TaskSqlConfig sqlConfig) {
        return sqlConfig.getExcelLoopEnabled() != null && sqlConfig.getExcelLoopEnabled() == 1
                && StringUtils.hasText(sqlConfig.getExcelLoopConfig());
    }

    public List<LoopIterationResult> expandLoop(TaskSqlConfig sqlConfig, Map<String, Object> baseParams) throws Exception {
        ExcelLoopConfig loopConfig = parseLoopConfig(sqlConfig.getExcelLoopConfig());
        if (loopConfig == null) {
            return Collections.emptyList();
        }

        String startStr = PlaceholderUtils.replacePlaceholders(loopConfig.getStartExpr(), baseParams);
        String endStr = PlaceholderUtils.replacePlaceholders(loopConfig.getEndExpr(), baseParams);
        if (!StringUtils.hasText(startStr) || !StringUtils.hasText(endStr)) {
            throw new IllegalArgumentException("Excel 循环配置的开始/结束表达式不能为空");
        }

        Temporal start = parseTemporal(startStr);
        Temporal end = parseTemporal(endStr);
        String unit = loopConfig.getUnit().toUpperCase();
        start = normalizeTemporal(start, unit);
        end = normalizeTemporal(end, unit);
        int step = loopConfig.getStep();

        List<LoopIterationResult> results = new ArrayList<>();
        Temporal current = start;
        int index = 0;
        while (true) {
            if (!isBeforeOrEqual(current, end, unit)) {
                break;
            }
            if (!evaluateCondition(loopConfig.getCondition(), index, current, start, end)) {
                break;
            }

            Map<String, Object> iterationParams = buildIterationParams(baseParams, index, current, start, end);
            List<Map<String, Object>> data = sqlExecutor.executeQuery(
                    sqlConfig.getDatasourceId(), sqlConfig.getSqlContent(), iterationParams);

            String sheetName = resolveSheetName(loopConfig.getSheetNameExpr(), iterationParams, sqlConfig);

            if (!loopConfig.isSkipEmptySheet() || (data != null && !data.isEmpty())) {
                List<Map<String, Object>> taggedData = tagWithSheetName(data, sheetName);
                results.add(new LoopIterationResult(sheetName, taggedData));
            }

            index++;
            current = plus(current, unit, step);
        }
        return results;
    }

    private ExcelLoopConfig parseLoopConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, ExcelLoopConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel 循环配置格式错误: " + e.getMessage(), e);
        }
    }

    private Temporal parseTemporal(String value) {
        String trimmed = value.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy-MM"),
                DateTimeFormatter.ofPattern("yyyy")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return YearMonth.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return Year.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException("无法解析时间表达式: " + trimmed);
    }

    private Temporal normalizeTemporal(Temporal temporal, String unit) {
        return switch (unit) {
            case "MONTH" -> {
                if (temporal instanceof YearMonth ym) {
                    yield ym;
                }
                if (temporal instanceof LocalDate ld) {
                    yield YearMonth.from(ld);
                }
                if (temporal instanceof LocalDateTime ldt) {
                    yield YearMonth.from(ldt);
                }
                throw new IllegalArgumentException("循环单位 MONTH 需要年月类型开始/结束值");
            }
            case "DAY", "WEEK" -> {
                if (temporal instanceof LocalDate ld) {
                    yield ld;
                }
                if (temporal instanceof LocalDateTime ldt) {
                    yield ldt.toLocalDate();
                }
                if (temporal instanceof YearMonth ym) {
                    yield ym.atDay(1);
                }
                throw new IllegalArgumentException("循环单位 DAY/WEEK 需要日期类型开始/结束值");
            }
            case "YEAR" -> {
                if (temporal instanceof Year y) {
                    yield y;
                }
                if (temporal instanceof LocalDate ld) {
                    yield Year.from(ld);
                }
                if (temporal instanceof LocalDateTime ldt) {
                    yield Year.from(ldt);
                }
                throw new IllegalArgumentException("循环单位 YEAR 需要年份类型开始/结束值");
            }
            case "HOUR", "MINUTE" -> {
                if (temporal instanceof LocalDateTime ldt) {
                    yield ldt;
                }
                if (temporal instanceof LocalDate ld) {
                    yield ld.atStartOfDay();
                }
                throw new IllegalArgumentException("循环单位 HOUR/MINUTE 需要日期时间类型开始/结束值");
            }
            default -> throw new IllegalArgumentException("不支持的循环单位: " + unit);
        };
    }

    private boolean isBeforeOrEqual(Temporal current, Temporal end, String unit) {
        LocalDateTime currentDt = toDateTime(current, unit);
        LocalDateTime endDt = toDateTime(end, unit);
        return !currentDt.isAfter(endDt);
    }

    private Temporal plus(Temporal current, String unit, int step) {
        return switch (unit) {
            case "MONTH" -> ((YearMonth) current).plusMonths(step);
            case "DAY" -> ((LocalDate) current).plusDays(step);
            case "WEEK" -> ((LocalDate) current).plusWeeks(step);
            case "YEAR" -> ((Year) current).plusYears(step);
            case "HOUR" -> ((LocalDateTime) current).plusHours(step);
            case "MINUTE" -> ((LocalDateTime) current).plusMinutes(step);
            default -> throw new IllegalArgumentException("不支持的循环单位: " + unit);
        };
    }

    private ChronoUnit toChronoUnit(String unit) {
        return switch (unit) {
            case "MONTH" -> ChronoUnit.MONTHS;
            case "DAY" -> ChronoUnit.DAYS;
            case "WEEK" -> ChronoUnit.WEEKS;
            case "YEAR" -> ChronoUnit.YEARS;
            case "HOUR" -> ChronoUnit.HOURS;
            case "MINUTE" -> ChronoUnit.MINUTES;
            default -> throw new IllegalArgumentException("不支持的循环单位: " + unit);
        };
    }

    private LocalDateTime toDateTime(Temporal temporal, String unit) {
        return switch (unit) {
            case "MONTH" -> ((YearMonth) temporal).atDay(1).atStartOfDay();
            case "DAY", "WEEK" -> ((LocalDate) temporal).atStartOfDay();
            case "YEAR" -> ((Year) temporal).atDay(1).atStartOfDay();
            case "HOUR", "MINUTE" -> (LocalDateTime) temporal;
            default -> throw new IllegalArgumentException("不支持的循环单位: " + unit);
        };
    }

    private Map<String, Object> buildIterationParams(Map<String, Object> baseParams, int index,
                                                     Temporal current, Temporal start, Temporal end) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (baseParams != null) {
            params.putAll(baseParams);
        }
        params.put("loopIndex", index);
        params.put("loopValue", current);
        params.put("loopDate", current);
        params.put("loopStart", start);
        params.put("loopEnd", end);
        LocalDateTime[] range = toStartEndDateTime(current);
        params.put("loopStartTime", range[0]);
        params.put("loopEndTime", range[1]);
        return params;
    }

    private LocalDateTime[] toStartEndDateTime(Temporal current) {
        if (current instanceof YearMonth ym) {
            return new LocalDateTime[]{
                    ym.atDay(1).atStartOfDay(),
                    ym.atEndOfMonth().atTime(23, 59, 59)
            };
        }
        if (current instanceof LocalDate ld) {
            return new LocalDateTime[]{
                    ld.atStartOfDay(),
                    ld.atTime(23, 59, 59)
            };
        }
        if (current instanceof Year y) {
            return new LocalDateTime[]{
                    y.atDay(1).atStartOfDay(),
                    y.atMonth(12).atEndOfMonth().atTime(23, 59, 59)
            };
        }
        if (current instanceof LocalDateTime ldt) {
            return new LocalDateTime[]{ldt, ldt};
        }
        throw new IllegalArgumentException("无法计算循环起止时间: " + current);
    }

    private boolean evaluateCondition(String condition, int index, Temporal current, Temporal start, Temporal end) {
        if (!StringUtils.hasText(condition)) {
            return true;
        }
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext();
            ctx.setVariable("loopIndex", index);
            ctx.setVariable("loopValue", current);
            ctx.setVariable("loopDate", current);
            ctx.setVariable("loopStart", start);
            ctx.setVariable("loopEnd", end);
            Boolean result = SPEL_PARSER.parseExpression(condition).getValue(ctx, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.warn("Excel 循环条件表达式执行失败: {}", condition, e);
            return true;
        }
    }

    private String resolveSheetName(String sheetNameExpr, Map<String, Object> params, TaskSqlConfig sqlConfig) {
        String expr = StringUtils.hasText(sheetNameExpr) ? sheetNameExpr : sqlConfig.getExcelSheetName();
        if (!StringUtils.hasText(expr)) {
            expr = sqlConfig.getSqlName();
        }
        return PlaceholderUtils.replacePlaceholders(expr, params);
    }

    private List<Map<String, Object>> tagWithSheetName(List<Map<String, Object>> data, String sheetName) {
        if (data == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>(data.size());
        for (Map<String, Object> row : data) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.put("_sheet_name", sheetName);
            result.add(copy);
        }
        return result;
    }

    public record LoopIterationResult(String sheetName, List<Map<String, Object>> data) {
    }
}
