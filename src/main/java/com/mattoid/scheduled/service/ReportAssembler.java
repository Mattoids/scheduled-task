package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.entity.TaskSqlGroup;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.task.TaskExecutionResult;
import com.mattoid.scheduled.util.PlaceholderUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 报表组装器：负责输出路径、文件名、临时模板及结果摘要的组装。
 */
@Component
public class ReportAssembler {

    private static final Pattern UNSAFE_FILENAME_CHAR_PATTERN = Pattern.compile("[\\\\/:*?\"\u003c\u003e|]");

    private final String uploadPath;
    private final TaskSqlGroupService taskSqlGroupService;

    public ReportAssembler(@Value("${report.upload.path}") String uploadPath,
                           TaskSqlGroupService taskSqlGroupService) {
        this.uploadPath = uploadPath;
        this.taskSqlGroupService = taskSqlGroupService;
    }

    public String assembleResultMessage(TaskExecutionResult result) {
        StringBuilder message = new StringBuilder("生成 ")
                .append(result.getReportFiles().size()).append(" 个报表文件");
        if (!result.getInlineResults().isEmpty()) {
            message.append("，").append(result.getInlineResults().size()).append(" 个结果内联发送");
        }
        if (!result.getChartFiles().isEmpty()) {
            message.append("，").append(result.getChartFiles().size()).append(" 个图表");
        }
        return message.toString();
    }

    public String assembleFilePath(TaskExecutionResult result) {
        List<File> reportFiles = result.getReportFiles();
        if (reportFiles.isEmpty()) {
            return null;
        }
        return reportFiles.stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(","));
    }

    public String buildOutputPath(TaskConfig task, TaskSqlConfig sqlConfig, String extension) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(task.getId()));
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String fileName = buildFileName(task, sqlConfig);
        fileName = ensureExtension(fileName, extension);
        return outputDir.resolve(fileName).toString();
    }

    public String buildOutputPath(TaskConfig task, TaskWebCrawlConfig crawlConfig, String extension) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(task.getId()));
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String fileName = buildFileName(task, crawlConfig);
        fileName = ensureExtension(fileName, extension);
        return outputDir.resolve(fileName).toString();
    }

    public String buildTempOutputPath(Long taskId, String templateType, int stepIndex) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(taskId), "temp");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String ext = resolveExtension(templateType, null);
        String name = "temp_" + stepIndex + "_" + System.currentTimeMillis() + "." + ext;
        return outputDir.resolve(name).toString();
    }

    public String buildTempOutputPath(Long taskId, String extension) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(taskId), "temp");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String ext = StringUtils.hasText(extension) ? extension : "tmp";
        String name = "temp_" + System.currentTimeMillis() + "." + ext;
        return outputDir.resolve(name).toString();
    }

    public String buildTempOutputPath(Long taskId, String extension, String fileName) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(taskId), "temp");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        if (!StringUtils.hasText(fileName)) {
            return buildTempOutputPath(taskId, extension);
        }
        return outputDir.resolve(fileName).toString();
    }

    public String buildFileName(TaskConfig task, TaskWebCrawlConfig crawlConfig) {
        String pattern = null;
        if (crawlConfig != null) {
            pattern = crawlConfig.getFileNamePattern();
        }
        if (!StringUtils.hasText(pattern)) {
            if (crawlConfig != null && StringUtils.hasText(crawlConfig.getCrawlName())) {
                pattern = crawlConfig.getCrawlName();
            } else {
                pattern = "report_{yyyyMMddHHmmss}";
            }
        }
        String fileName = PlaceholderUtils.replacePlaceholders(pattern);
        return UNSAFE_FILENAME_CHAR_PATTERN.matcher(fileName).replaceAll("_");
    }

    public String buildFileName(TaskConfig task, TaskSqlConfig sqlConfig) {
        String pattern = null;
        if (sqlConfig != null) {
            pattern = sqlConfig.getFileNamePattern();
            if (!StringUtils.hasText(pattern) && sqlConfig.getTaskSqlGroup() != null) {
                pattern = sqlConfig.getTaskSqlGroup().getFileNamePattern();
            }
            if (!StringUtils.hasText(pattern) && sqlConfig.getGroupId() != null) {
                TaskSqlGroup group = taskSqlGroupService.getById(sqlConfig.getGroupId());
                if (group != null) {
                    pattern = group.getFileNamePattern();
                }
            }
        }
        if (!StringUtils.hasText(pattern)) {
            if (sqlConfig != null && StringUtils.hasText(sqlConfig.getSqlName())) {
                pattern = sqlConfig.getSqlName();
            } else {
                pattern = "report_{yyyyMMddHHmmss}";
            }
        }
        String fileName = PlaceholderUtils.replacePlaceholders(pattern);
        return UNSAFE_FILENAME_CHAR_PATTERN.matcher(fileName).replaceAll("_");
    }

    public String ensureExtension(String fileName, String extension) {
        String lower = fileName.toLowerCase();
        String dotExt = "." + extension.toLowerCase();
        if (lower.endsWith(dotExt)) {
            return fileName;
        }
        return fileName + dotExt;
    }

    public String resolveExtension(String outputFormat, String fileSuffix) {
        if (StringUtils.hasText(fileSuffix)) {
            return fileSuffix.startsWith(".") ? fileSuffix.substring(1) : fileSuffix;
        }
        return switch (outputFormat.toUpperCase()) {
            case "EXCEL" -> "xlsx";
            case "WORD" -> "docx";
            case "PPT" -> "pptx";
            case "TXT" -> "txt";
            default -> "csv";
        };
    }

    public File resolveTemplateFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("模板文件路径为空");
        }
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path.toFile();
        }
        return Paths.get(uploadPath, filePath).toFile();
    }

    public File resolveBaseFile(TaskSqlConfig sqlConfig) {
        String baseFilePath = sqlConfig.getExcelBaseFilePath();
        if (!StringUtils.hasText(baseFilePath)) {
            return null;
        }
        String resolved = PlaceholderUtils.replacePlaceholders(baseFilePath);
        Path path = Paths.get(resolved);
        if (path.isAbsolute()) {
            return path.toFile();
        }
        return Paths.get(uploadPath, resolved).toFile();
    }

    public File createTempCsvTemplate(List<java.util.Map<String, Object>> data) throws Exception {
        Path temp = Files.createTempFile("template", ".csv");
        if (!data.isEmpty()) {
            String header = String.join(",", data.get(0).keySet());
            Files.writeString(temp, header + "\n");
        }
        return temp.toFile();
    }

    public File createTempTemplate(String outputFormat, List<java.util.Map<String, Object>> data) throws Exception {
        Path temp = Files.createTempFile("template", "." + outputFormat.toLowerCase());
        String header = data.isEmpty() ? "" : String.join(",", data.get(0).keySet());
        switch (outputFormat.toUpperCase()) {
            case "TXT" -> Files.writeString(temp, "${" + header.replace(",", "} ${") + "}");
            default -> Files.writeString(temp, header);
        }
        return temp.toFile();
    }
}
