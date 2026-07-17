package com.mattoid.scheduled.notification.support;

import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.event.InlineResult;
import com.mattoid.scheduled.event.TaskExecutionEvent;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 通知内容构建静态工具，供各渠道实现复用。
 */
public final class NotificationContentHelper {

    private static final Pattern CHART_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{chart:([^}]+)\\}");

    private NotificationContentHelper() {
    }

    public static String buildDefaultSubject(TaskExecutionEvent event) {
        return "任务执行通知 - " + event.getTask().getTaskName();
    }

    public static String buildDefaultSummary(TaskExecutionEvent event) {
        TaskConfig task = event.getTask();
        var log = event.getTaskLog();
        StringBuilder sb = new StringBuilder();
        sb.append("任务执行通知\n");
        sb.append("任务: ").append(task.getTaskName()).append("\n");
        sb.append("状态: ").append(log.getStatus()).append("\n");
        if (log.getStartTime() != null && log.getEndTime() != null) {
            long seconds = java.time.Duration.between(log.getStartTime(), log.getEndTime()).getSeconds();
            sb.append("耗时: ").append(seconds).append("s\n");
        }
        if (StringUtils.hasText(log.getResultMessage())) {
            sb.append("结果: ").append(log.getResultMessage()).append("\n");
        }
        if (StringUtils.hasText(log.getErrorMessage())) {
            sb.append("错误: ").append(log.getErrorMessage()).append("\n");
        }
        if (StringUtils.hasText(log.getFilePath())) {
            sb.append("文件: ").append(log.getFilePath());
        }
        return sb.toString();
    }

    public static String buildAiNotificationContext(TaskConfig task, TaskExecutionEvent event) {
        List<File> reportFiles = event.getNotifyFiles();
        List<? extends InlineResult> inlineResults = event.getInlineResults();
        StringBuilder sb = new StringBuilder();
        sb.append("任务名称: ").append(task.getTaskName()).append("\n");
        sb.append("任务编码: ").append(task.getTaskCode()).append("\n");
        sb.append("触发类型: ").append(task.getTriggerType()).append("\n");
        sb.append("附件数量: ").append(reportFiles != null ? reportFiles.size() : 0).append("\n");
        if (reportFiles != null && !reportFiles.isEmpty()) {
            sb.append("附件名称: ");
            for (int i = 0; i < reportFiles.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(reportFiles.get(i).getName());
            }
            sb.append("\n");
        }
        sb.append("内联 SQL 结果数量: ").append(inlineResults != null ? inlineResults.size() : 0).append("\n");
        if (inlineResults != null) {
            for (InlineResult result : inlineResults) {
                sb.append("  - ").append(result.name())
                        .append("(")
                        .append(result.code())
                        .append("): ")
                        .append(result.data().size())
                        .append(" 行\n");
            }
        }
        return sb.toString();
    }

    public static Map<String, Object> buildInlineResultContext(List<? extends InlineResult> inlineResults) {
        if (inlineResults == null || inlineResults.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> context = new LinkedHashMap<>();
        for (InlineResult result : inlineResults) {
            if (result.data() == null || result.data().isEmpty()) {
                continue;
            }
            List<Map<String, Object>> rows = result.data();
            Set<String> columnNames = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                columnNames.addAll(row.keySet());
            }
            for (String col : columnNames) {
                List<Object> values = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    values.add(row.get(col));
                }
                if (rows.size() == 1 && columnNames.size() == 1) {
                    context.put(col, values.get(0));
                } else {
                    context.put(col, values);
                }
            }
            context.put(result.name() + "_count", rows.size());
        }
        return context;
    }

    public static String formatWeComMarkdown(String content, String title) {
        return title + "\n\n" + content;
    }

    public static String formatWeComText(String content, String title) {
        return title + "\n\n" + content;
    }

    public static List<String> parseMentionedList(String wecomToUser) {
        if (!StringUtils.hasText(wecomToUser)) {
            return Collections.emptyList();
        }
        return Arrays.stream(wecomToUser.split("\\|"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    public static String replaceChartPlaceholdersForEmail(String content,
                                                          Map<String, File> chartFiles,
                                                          Map<String, File> inlineImages) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        Matcher matcher = CHART_PLACEHOLDER_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String sqlCode = matcher.group(1).trim();
            File chartFile = chartFiles != null ? chartFiles.get(sqlCode) : null;
            if (chartFile != null && chartFile.exists()) {
                String cid = "chart_" + sqlCode;
                inlineImages.put(cid, chartFile);
                matcher.appendReplacement(sb, "<img src=\"cid:" + Matcher.quoteReplacement(cid) + "\" />");
            } else {
                matcher.appendReplacement(sb, "[图表未生成: " + Matcher.quoteReplacement(sqlCode) + "]");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static ChartPlaceholderResult replaceChartPlaceholdersForWeCom(String content, Map<String, File> chartFiles) {
        if (!StringUtils.hasText(content)) {
            return new ChartPlaceholderResult(content, Collections.emptyList());
        }
        List<File> images = new ArrayList<>();
        Matcher matcher = CHART_PLACEHOLDER_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String sqlCode = matcher.group(1).trim();
            File chartFile = chartFiles != null ? chartFiles.get(sqlCode) : null;
            if (chartFile != null && chartFile.exists()) {
                images.add(chartFile);
                matcher.appendReplacement(sb, "[图表: " + Matcher.quoteReplacement(sqlCode) + "]");
            } else {
                matcher.appendReplacement(sb, "[图表未生成: " + Matcher.quoteReplacement(sqlCode) + "]");
            }
        }
        matcher.appendTail(sb);
        return new ChartPlaceholderResult(sb.toString(), images);
    }

    public static String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    public record ChartPlaceholderResult(String content, List<File> images) {
    }
}
