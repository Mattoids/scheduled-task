package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.event.InlineSqlResult;
import com.mattoid.scheduled.event.TaskExecutionEvent;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.task.SqlExecutor;
import com.mattoid.scheduled.template.TemplateProcessor;
import com.mattoid.scheduled.template.TemplateProcessorFactory;
import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TaskExecutionService {

    private static final Pattern UNSAFE_FILENAME_CHAR_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");

    @Value("${report.upload.path}")
    private String uploadPath;

    private final TaskConfigMapper taskConfigMapper;
    private final TaskLogMapper taskLogMapper;
    private final SqlExecutor sqlExecutor;
    private final ReportTemplateService reportTemplateService;
    private final TaskSqlConfigService taskSqlConfigService;
    private final TemplateProcessorFactory templateProcessorFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskSqlGroupService taskSqlGroupService;

    public TaskExecutionService(TaskConfigMapper taskConfigMapper,
                                TaskLogMapper taskLogMapper,
                                SqlExecutor sqlExecutor,
                                ReportTemplateService reportTemplateService,
                                TaskSqlConfigService taskSqlConfigService,
                                TemplateProcessorFactory templateProcessorFactory,
                                ApplicationEventPublisher eventPublisher,
                                TaskSqlGroupService taskSqlGroupService) {
        this.taskConfigMapper = taskConfigMapper;
        this.taskLogMapper = taskLogMapper;
        this.sqlExecutor = sqlExecutor;
        this.reportTemplateService = reportTemplateService;
        this.taskSqlConfigService = taskSqlConfigService;
        this.templateProcessorFactory = templateProcessorFactory;
        this.eventPublisher = eventPublisher;
        this.taskSqlGroupService = taskSqlGroupService;
    }

    public void executeTask(Long taskId, String triggerMode) {
        TaskConfig task = taskConfigMapper.selectById(taskId);
        if (task == null) {
            log.error("任务不存在: {}", taskId);
            return;
        }

        TaskLog logEntity = new TaskLog();
        logEntity.setTaskId(taskId);
        logEntity.setTriggerMode(triggerMode);
        logEntity.setStartTime(LocalDateTime.now());
        logEntity.setStatus("RUNNING");
        taskLogMapper.insert(logEntity);

        List<File> reportFiles = new ArrayList<>();
        List<InlineSqlResult> inlineResults = new ArrayList<>();
        try {
            List<TaskSqlConfig> sqlConfigs = taskSqlConfigService.listByTaskId(taskId);
            if (sqlConfigs.isEmpty()) {
                throw new IllegalArgumentException("任务未配置 SQL 模块");
            }

            SqlExecutionResults results = executeSqlConfigs(task, sqlConfigs);
            reportFiles = results.getFiles();
            inlineResults = results.getInlineResults();
            StringBuilder resultMsg = new StringBuilder("生成 ")
                    .append(reportFiles.size()).append(" 个报表文件");
            if (!inlineResults.isEmpty()) {
                resultMsg.append("，").append(inlineResults.size()).append(" 个 SQL 结果内联发送");
            }
            logEntity.setResultMessage(resultMsg.toString());

            if (!reportFiles.isEmpty()) {
                List<String> filePaths = reportFiles.stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.toList());
                logEntity.setFilePath(String.join(",", filePaths));
            }

            logEntity.setStatus("SUCCESS");
        } catch (Exception e) {
            log.error("任务执行失败: {}", taskId, e);
            logEntity.setStatus("FAILED");
            logEntity.setErrorMessage(e.getMessage());
        } finally {
            logEntity.setEndTime(LocalDateTime.now());
            taskLogMapper.updateById(logEntity);
            publishTaskExecutionEvents(task, logEntity, reportFiles, inlineResults);
        }
    }

    private void publishTaskExecutionEvents(TaskConfig task, TaskLog logEntity, List<File> reportFiles,
                                            List<InlineSqlResult> inlineResults) {
        String status = logEntity.getStatus();
        if ("SUCCESS".equals(status)) {
            eventPublisher.publishEvent(new TaskExecutionEvent(this, task, logEntity, reportFiles, inlineResults, TaskExecutionEvent.EventType.TASK_SUCCESS));
        }
        if ("FAILED".equals(status)) {
            eventPublisher.publishEvent(new TaskExecutionEvent(this, task, logEntity, reportFiles, inlineResults, TaskExecutionEvent.EventType.TASK_FAILURE));
        }
        eventPublisher.publishEvent(new TaskExecutionEvent(this, task, logEntity, reportFiles, inlineResults, TaskExecutionEvent.EventType.TASK_COMPLETED));
    }

    @Async
    public void executeTaskAsync(Long taskId, String triggerMode) {
        executeTask(taskId, triggerMode);
    }

    private SqlExecutionResults executeSqlConfigs(TaskConfig task, List<TaskSqlConfig> sqlConfigs) throws Exception {
        Map<Object, List<TaskSqlConfig>> groups = new LinkedHashMap<>();
        for (TaskSqlConfig sql : sqlConfigs) {
            Object key = sql.getTemplateId() != null ? sql.getTemplateId() : "sql_" + sql.getId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(sql);
        }

        SqlExecutionResults results = new SqlExecutionResults();
        for (Map.Entry<Object, List<TaskSqlConfig>> entry : groups.entrySet()) {
            List<TaskSqlConfig> group = entry.getValue();
            if (group.get(0).getTemplateId() != null) {
                Long templateId = group.get(0).getTemplateId();
                ReportTemplate template = reportTemplateService.getById(templateId);
                if (template == null) {
                    throw new IllegalArgumentException("模板不存在: " + templateId);
                }
                results.addFile(processTemplateChain(task, template, group));
            } else {
                for (TaskSqlConfig sql : group) {
                    List<Map<String, Object>> data = sqlExecutor.executeQuery(sql.getDatasourceId(), sql.getSqlContent());
                    if ("INLINE".equalsIgnoreCase(sql.getOutputFormat())) {
                        results.addInline(new InlineSqlResult(sql.getSqlName(), sql.getSqlCode(), data));
                    } else {
                        results.addFile(generateSqlOutputFile(task, sql, data));
                    }
                }
            }
        }
        return results;
    }

    private static class SqlExecutionResults {
        private final List<File> files = new ArrayList<>();
        private final List<InlineSqlResult> inlineResults = new ArrayList<>();

        public void addFile(File file) {
            files.add(file);
        }

        public void addInline(InlineSqlResult inlineResult) {
            inlineResults.add(inlineResult);
        }

        public List<File> getFiles() {
            return files;
        }

        public List<InlineSqlResult> getInlineResults() {
            return inlineResults;
        }
    }

    private File processTemplateChain(TaskConfig task, ReportTemplate template, List<TaskSqlConfig> sqlConfigs) throws Exception {
        String templateType = template.getTemplateType();
        TemplateProcessor processor = templateProcessorFactory.getProcessor(templateType);
        File templateFile = resolveTemplateFile(template.getFilePath());
        String extension = resolveExtension(templateType, sqlConfigs.get(0).getFileSuffix());
        String outputFileName = buildOutputPath(task, sqlConfigs.get(0), extension);

        File currentFile = templateFile;
        File previousTempFile = null;
        for (int i = 0; i < sqlConfigs.size(); i++) {
            TaskSqlConfig sql = sqlConfigs.get(i);
            List<Map<String, Object>> data = sqlExecutor.executeQuery(sql.getDatasourceId(), sql.getSqlContent());
            boolean isLast = i == sqlConfigs.size() - 1;
            String stepOutput = isLast ? outputFileName : buildTempOutputPath(task.getId(), templateType, i);
            currentFile = processor.process(currentFile, data, stepOutput, isLast);
            if (previousTempFile != null) {
                Files.deleteIfExists(previousTempFile.toPath());
            }
            previousTempFile = isLast ? null : currentFile;
        }
        return new File(outputFileName);
    }

    private File resolveTemplateFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("模板文件路径为空");
        }
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path.toFile();
        }
        return Paths.get(uploadPath, filePath).toFile();
    }

    private File generateSqlOutputFile(TaskConfig task, TaskSqlConfig sqlConfig, List<Map<String, Object>> data) throws Exception {
        String outputFormat = StringUtils.hasText(sqlConfig.getOutputFormat()) ? sqlConfig.getOutputFormat() : "CSV";
        String upperFormat = outputFormat.toUpperCase();
        String extension = resolveExtension(upperFormat, sqlConfig.getFileSuffix());
        String outputPath = buildOutputPath(task, sqlConfig, extension);

        return switch (upperFormat) {
            case "CSV" -> templateProcessorFactory.getProcessor("CSV")
                    .process(createTempCsvTemplate(data), data, outputPath);
            case "EXCEL" -> generateExcelFromData(data, outputPath);
            case "TXT" -> {
                File templateFile = createTempTemplate(upperFormat, data);
                yield templateProcessorFactory.getProcessor(upperFormat)
                        .process(templateFile, data, outputPath, true);
            }
            default -> {
                String csvPath = buildOutputPath(task, sqlConfig, resolveExtension("CSV", null));
                yield templateProcessorFactory.getProcessor("CSV")
                        .process(createTempCsvTemplate(data), data, csvPath);
            }
        };
    }

    private boolean isSequenceHeader(String header) {
        if (header == null) {
            return false;
        }
        String trimmed = header.trim();
        return "序号".equals(trimmed) || "seq".equalsIgnoreCase(trimmed);
    }

    private File generateExcelFromData(List<Map<String, Object>> data, String outputPath) throws Exception {
        File output = new File(outputPath);
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(output)) {
            Sheet sheet = workbook.createSheet("Sheet1");
            if (!data.isEmpty()) {
                List<String> headers = new ArrayList<>(data.get(0).keySet());
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers.get(i));
                }
                for (int i = 0; i < data.size(); i++) {
                    Row row = sheet.createRow(i + 1);
                    Map<String, Object> rowData = data.get(i);
                    for (int c = 0; c < headers.size(); c++) {
                        String header = headers.get(c);
                        Object value = isSequenceHeader(header) ? i + 1 : rowData.get(header);
                        setExcelCellValue(row.createCell(c), value);
                    }
                }
            }
            workbook.write(fos);
        }
        return output;
    }

    private void setExcelCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private File createTempCsvTemplate(List<Map<String, Object>> data) throws Exception {
        Path temp = Files.createTempFile("template", ".csv");
        if (!data.isEmpty()) {
            String header = String.join(",", data.get(0).keySet());
            Files.writeString(temp, header + "\n");
        }
        return temp.toFile();
    }

    private File createTempTemplate(String outputFormat, List<Map<String, Object>> data) throws Exception {
        Path temp = Files.createTempFile("template", "." + outputFormat.toLowerCase());
        String header = data.isEmpty() ? "" : String.join(",", data.get(0).keySet());
        switch (outputFormat.toUpperCase()) {
            case "TXT" -> Files.writeString(temp, "${" + header.replace(",", "} ${") + "}");
            default -> Files.writeString(temp, header);
        }
        return temp.toFile();
    }

    private String resolveExtension(String outputFormat, String fileSuffix) {
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

    private String buildOutputPath(TaskConfig task, TaskSqlConfig sqlConfig, String extension) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(task.getId()));
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String fileName = buildFileName(task, sqlConfig);
        fileName = ensureExtension(fileName, extension);
        return outputDir.resolve(fileName).toString();
    }

    private String buildTempOutputPath(Long taskId, String templateType, int stepIndex) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(taskId), "temp");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String ext = resolveExtension(templateType, null);
        String name = "temp_" + stepIndex + "_" + System.currentTimeMillis() + "." + ext;
        return outputDir.resolve(name).toString();
    }

    private String buildFileName(TaskConfig task, TaskSqlConfig sqlConfig) {
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
            pattern = "report_{yyyyMMddHHmmss}";
        }
        String fileName = PlaceholderUtils.replacePlaceholders(pattern);
        return UNSAFE_FILENAME_CHAR_PATTERN.matcher(fileName).replaceAll("_");
    }

    private String ensureExtension(String fileName, String extension) {
        String lower = fileName.toLowerCase();
        String dotExt = "." + extension.toLowerCase();
        if (lower.endsWith(dotExt)) {
            return fileName;
        }
        return fileName + dotExt;
    }
}
