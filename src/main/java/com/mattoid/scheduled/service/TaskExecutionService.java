package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.event.InlineSqlResult;
import com.mattoid.scheduled.event.TaskExecutionEvent;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.task.SqlExecutor;
import com.mattoid.scheduled.template.TemplateProcessor;
import com.mattoid.scheduled.template.TemplateProcessorFactory;
import com.mattoid.scheduled.util.PlaceholderUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    private final TaskDependencyService taskDependencyService;
    private final ChartGenerationService chartGenerationService;

    public TaskExecutionService(TaskConfigMapper taskConfigMapper,
                                TaskLogMapper taskLogMapper,
                                SqlExecutor sqlExecutor,
                                ReportTemplateService reportTemplateService,
                                TaskSqlConfigService taskSqlConfigService,
                                TemplateProcessorFactory templateProcessorFactory,
                                ApplicationEventPublisher eventPublisher,
                                TaskSqlGroupService taskSqlGroupService,
                                TaskDependencyService taskDependencyService,
                                ChartGenerationService chartGenerationService) {
        this.taskConfigMapper = taskConfigMapper;
        this.taskLogMapper = taskLogMapper;
        this.sqlExecutor = sqlExecutor;
        this.reportTemplateService = reportTemplateService;
        this.taskSqlConfigService = taskSqlConfigService;
        this.templateProcessorFactory = templateProcessorFactory;
        this.eventPublisher = eventPublisher;
        this.taskSqlGroupService = taskSqlGroupService;
        this.taskDependencyService = taskDependencyService;
        this.chartGenerationService = chartGenerationService;
    }

    public void executeTask(Long taskId, String triggerMode) {
        executeTask(taskId, triggerMode, java.util.Collections.emptyMap());
    }

    public void executeTask(Long taskId, String triggerMode, Map<String, Object> params) {
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

        // 防止同一个任务手动触发和 Quartz 自动触发并发执行
        Long runningCount = taskLogMapper.selectCount(
                new LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getTaskId, taskId)
                        .eq(TaskLog::getStatus, "RUNNING")
                        .lt(TaskLog::getId, logEntity.getId())
                        .ge(TaskLog::getStartTime, LocalDateTime.now().minusHours(1))
        );
        if (runningCount != null && runningCount > 0) {
            log.warn("任务正在执行中，跳过本次触发: taskId={}", taskId);
            logEntity.setStatus("SKIPPED");
            logEntity.setResultMessage("任务正在执行中，已跳过本次触发");
            logEntity.setEndTime(LocalDateTime.now());
            taskLogMapper.updateById(logEntity);
            return;
        }

        List<File> reportFiles = new ArrayList<>();
        List<InlineSqlResult> inlineResults = new ArrayList<>();
        Map<String, File> chartFiles = new LinkedHashMap<>();
        try {
            String taskCode = task.getTaskCode();
            List<TaskSqlConfig> sqlConfigs = taskSqlConfigService.listByTaskCode(taskCode);
            if (sqlConfigs.isEmpty()) {
                throw new IllegalArgumentException("任务未配置 SQL 模块");
            }

            SqlExecutionResults results = executeSqlConfigs(task, sqlConfigs, params);
            reportFiles = results.getFiles();
            inlineResults = results.getInlineResults();
            chartFiles = results.getChartFiles();
            StringBuilder resultMsg = new StringBuilder("生成 ")
                    .append(reportFiles.size()).append(" 个报表文件");
            if (!inlineResults.isEmpty()) {
                resultMsg.append("，").append(inlineResults.size()).append(" 个 SQL 结果内联发送");
            }
            if (!chartFiles.isEmpty()) {
                resultMsg.append("，").append(chartFiles.size()).append(" 个图表");
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
            publishTaskExecutionEvents(task, logEntity, reportFiles, inlineResults, chartFiles);
        }
    }

    private void publishTaskExecutionEvents(TaskConfig task, TaskLog logEntity, List<File> reportFiles,
                                            List<InlineSqlResult> inlineResults, Map<String, File> chartFiles) {
        String status = logEntity.getStatus();
        if ("SUCCESS".equals(status)) {
            eventPublisher.publishEvent(new TaskExecutionEvent(this, task, logEntity, reportFiles, inlineResults, chartFiles, TaskExecutionEvent.EventType.TASK_SUCCESS));
            eventPublisher.publishEvent(new TaskExecutionEvent(this, task, logEntity, reportFiles, inlineResults, chartFiles, TaskExecutionEvent.EventType.TASK_COMPLETED));
        } else if ("FAILED".equals(status)) {
            eventPublisher.publishEvent(new TaskExecutionEvent(this, task, logEntity, reportFiles, inlineResults, chartFiles, TaskExecutionEvent.EventType.TASK_FAILURE));
        }
    }

    @Async
    public void executeTaskAsync(Long taskId, String triggerMode) {
        executeTaskAsync(taskId, triggerMode, java.util.Collections.emptyMap());
    }

    @Async
    public void executeTaskAsync(Long taskId, String triggerMode, Map<String, Object> params) {
        executeTask(taskId, triggerMode, params);
    }

    @Async
    public void executeTaskAsyncWithDependencies(Long taskId, String triggerMode) {
        List<Long> sorted = taskDependencyService.topologicalSort(taskId);
        for (Long id : sorted) {
            executeTask(id, triggerMode);
            TaskLog latestLog = getLatestLog(id);
            if (latestLog == null || !"SUCCESS".equals(latestLog.getStatus())) {
                log.warn("依赖任务 {} 未成功执行，中止后续任务", id);
                break;
            }
        }
    }

    private TaskLog getLatestLog(Long taskId) {
        List<TaskLog> logs = taskLogMapper.selectList(
                new LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getTaskId, taskId)
                        .orderByDesc(TaskLog::getId)
                        .last("LIMIT 1")
        );
        return logs.isEmpty() ? null : logs.get(0);
    }

    private SqlExecutionResults executeSqlConfigs(TaskConfig task, List<TaskSqlConfig> sqlConfigs, Map<String, Object> params) throws Exception {
        Map<String, List<TaskSqlConfig>> groups = new LinkedHashMap<>();
        for (TaskSqlConfig sql : sqlConfigs) {
            String key = StringUtils.hasText(sql.getTemplateCode()) ? sql.getTemplateCode() : "sql_" + sql.getId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(sql);
        }

        SqlExecutionResults results = new SqlExecutionResults();
        for (Map.Entry<String, List<TaskSqlConfig>> entry : groups.entrySet()) {
            List<TaskSqlConfig> group = entry.getValue();
            if (StringUtils.hasText(group.get(0).getTemplateCode())) {
                String templateCode = group.get(0).getTemplateCode();
                ReportTemplate template = reportTemplateService.getByCode(templateCode);
                if (template == null) {
                    throw new IllegalArgumentException("模板编码不存在: " + templateCode);
                }
                results.addFile(processTemplateChain(task, template, group, params, results));
            } else {
                for (TaskSqlConfig sql : group) {
                    Map<String, Object> mergedParams = mergeSqlParams(sql, params);
                    List<Map<String, Object>> data = sqlExecutor.executeQuery(sql.getDatasourceId(), sql.getSqlContent(), mergedParams);
                    if ("INLINE".equalsIgnoreCase(sql.getOutputFormat())) {
                        results.addInline(new InlineSqlResult(sql.getSqlName(), sql.getSqlCode(), data));
                    } else {
                        results.addFile(generateSqlOutputFile(task, sql, data));
                    }
                    File chartFile = generateChartFile(task, sql, data);
                    if (chartFile != null) {
                        results.addChartFile(sql.getSqlCode(), chartFile);
                    }
                }
            }
        }
        return results;
    }

    private Map<String, Object> mergeSqlParams(TaskSqlConfig sqlConfig, Map<String, Object> params) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (StringUtils.hasText(sqlConfig.getCustomParams())) {
            try {
                Map<String, Object> customParams = OBJECT_MAPPER.readValue(sqlConfig.getCustomParams(), new TypeReference<Map<String, Object>>() {
                });
                if (customParams != null) {
                    merged.putAll(customParams);
                }
            } catch (Exception e) {
                log.warn("SQL 配置 customParams 解析失败, sqlId={}: {}", sqlConfig.getId(), e.getMessage());
            }
        }
        if (params != null) {
            merged.putAll(params);
        }
        return merged;
    }

    private static class SqlExecutionResults {
        private final List<File> files = new ArrayList<>();
        private final List<InlineSqlResult> inlineResults = new ArrayList<>();
        private final Map<String, File> chartFiles = new LinkedHashMap<>();

        public void addFile(File file) {
            files.add(file);
        }

        public void addInline(InlineSqlResult inlineResult) {
            inlineResults.add(inlineResult);
        }

        public void addChartFile(String sqlCode, File chartFile) {
            if (chartFile != null) {
                chartFiles.put(sqlCode, chartFile);
            }
        }

        public List<File> getFiles() {
            return files;
        }

        public List<InlineSqlResult> getInlineResults() {
            return inlineResults;
        }

        public Map<String, File> getChartFiles() {
            return chartFiles;
        }
    }

    private File processTemplateChain(TaskConfig task, ReportTemplate template, List<TaskSqlConfig> sqlConfigs,
                                      Map<String, Object> params, SqlExecutionResults results) throws Exception {
        String templateType = template.getTemplateType();
        TemplateProcessor processor = templateProcessorFactory.getProcessor(templateType);
        File templateFile = resolveTemplateFile(template.getFilePath());
        String extension = resolveExtension(templateType, sqlConfigs.get(0).getFileSuffix());
        String outputFileName = buildOutputPath(task, sqlConfigs.get(0), extension);

        File currentFile = templateFile;
        File previousTempFile = null;
        for (int i = 0; i < sqlConfigs.size(); i++) {
            TaskSqlConfig sql = sqlConfigs.get(i);
            Map<String, Object> mergedParams = mergeSqlParams(sql, params);
            List<Map<String, Object>> data = sqlExecutor.executeQuery(sql.getDatasourceId(), sql.getSqlContent(), mergedParams);
            File chartFile = generateChartFile(task, sql, data);
            if (chartFile != null) {
                results.addChartFile(sql.getSqlCode(), chartFile);
            }
            boolean isLast = i == sqlConfigs.size() - 1;
            String stepOutput = isLast ? outputFileName : buildTempOutputPath(task.getId(), templateType, i);
            Map<String, Object> context = buildProcessorContext(sql, chartFile);
            currentFile = processor.process(currentFile, data, stepOutput, isLast, context);
            if (previousTempFile != null) {
                Files.deleteIfExists(previousTempFile.toPath());
            }
            previousTempFile = isLast ? null : currentFile;
        }
        return new File(outputFileName);
    }

    private Map<String, Object> buildProcessorContext(TaskSqlConfig sql, File chartFile) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sqlId", sql.getId());
        context.put("sqlCode", sql.getSqlCode());
        context.put("sqlName", sql.getSqlName());
        context.put("chartEnabled", sql.getChartEnabled());
        context.put("chartType", sql.getChartType());
        context.put("chartTitle", sql.getChartTitle());
        context.put("chartFile", chartFile);
        return context;
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

    private File generateChartFile(TaskConfig task, TaskSqlConfig sqlConfig, List<Map<String, Object>> data) {
        if (sqlConfig.getChartEnabled() == null || sqlConfig.getChartEnabled() != 1) {
            return null;
        }
        if (data == null || data.isEmpty()) {
            log.warn("SQL {} 启用图表但结果为空，跳过生成", sqlConfig.getSqlCode());
            return null;
        }
        String chartType = StringUtils.hasText(sqlConfig.getChartType()) ? sqlConfig.getChartType() : "BAR";
        String title = StringUtils.hasText(sqlConfig.getChartTitle()) ? sqlConfig.getChartTitle() : sqlConfig.getSqlName();
        boolean autoMerge = sqlConfig.getChartAutoMerge() == null || sqlConfig.getChartAutoMerge() == 1;
        String labelRotation = StringUtils.hasText(sqlConfig.getChartLabelRotation()) ? sqlConfig.getChartLabelRotation() : "AUTO";
        File chartFile = chartGenerationService.generateChart(data, chartType, title, autoMerge, labelRotation);
        if (chartFile == null) {
            log.warn("SQL {} 图表生成失败", sqlConfig.getSqlCode());
        } else {
            log.info("SQL {} 图表生成成功: {}", sqlConfig.getSqlCode(), chartFile.getAbsolutePath());
        }
        return chartFile;
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
            if (sqlConfig != null && StringUtils.hasText(sqlConfig.getSqlName())) {
                pattern = sqlConfig.getSqlName();
            } else {
                pattern = "report_{yyyyMMddHHmmss}";
            }
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
