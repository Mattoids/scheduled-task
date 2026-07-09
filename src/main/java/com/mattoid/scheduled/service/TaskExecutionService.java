package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.event.InlineResult;
import com.mattoid.scheduled.event.InlineCrawlResult;
import com.mattoid.scheduled.event.InlineSqlResult;
import com.mattoid.scheduled.event.TaskExecutionEvent;
import com.mattoid.scheduled.task.WebCrawlExecutor;
import com.mattoid.scheduled.task.WebCrawlResult;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.task.SqlExecutor;
import com.mattoid.scheduled.template.TemplateProcessor;
import com.mattoid.scheduled.template.TemplateProcessorFactory;
import com.mattoid.scheduled.util.PlaceholderUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    private final TaskWebCrawlConfigService taskWebCrawlConfigService;
    private final WebCrawlExecutor webCrawlExecutor;
    private final TemplateProcessorFactory templateProcessorFactory;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskSqlGroupService taskSqlGroupService;
    private final TaskDependencyService taskDependencyService;
    private final ChartGenerationService chartGenerationService;
    private final ExcelGenerationService excelGenerationService;
    private final ExcelLoopHelper excelLoopHelper;
    private final ObjectMapper objectMapper;

    public TaskExecutionService(TaskConfigMapper taskConfigMapper,
                                TaskLogMapper taskLogMapper,
                                SqlExecutor sqlExecutor,
                                ReportTemplateService reportTemplateService,
                                TaskSqlConfigService taskSqlConfigService,
                                TaskWebCrawlConfigService taskWebCrawlConfigService,
                                WebCrawlExecutor webCrawlExecutor,
                                TemplateProcessorFactory templateProcessorFactory,
                                ApplicationEventPublisher eventPublisher,
                                TaskSqlGroupService taskSqlGroupService,
                                TaskDependencyService taskDependencyService,
                                ChartGenerationService chartGenerationService,
                                ExcelGenerationService excelGenerationService,
                                ExcelLoopHelper excelLoopHelper,
                                ObjectMapper objectMapper) {
        this.taskConfigMapper = taskConfigMapper;
        this.taskLogMapper = taskLogMapper;
        this.sqlExecutor = sqlExecutor;
        this.reportTemplateService = reportTemplateService;
        this.taskSqlConfigService = taskSqlConfigService;
        this.taskWebCrawlConfigService = taskWebCrawlConfigService;
        this.webCrawlExecutor = webCrawlExecutor;
        this.templateProcessorFactory = templateProcessorFactory;
        this.eventPublisher = eventPublisher;
        this.taskSqlGroupService = taskSqlGroupService;
        this.taskDependencyService = taskDependencyService;
        this.chartGenerationService = chartGenerationService;
        this.excelGenerationService = excelGenerationService;
        this.excelLoopHelper = excelLoopHelper;
        this.objectMapper = objectMapper;
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
        List<File> notifyFiles = new ArrayList<>();
        List<InlineResult> inlineResults = new ArrayList<>();
        Map<String, File> chartFiles = new LinkedHashMap<>();
        try {
            String taskCode = task.getTaskCode();
            if ("CRAWL".equalsIgnoreCase(task.getTaskType())) {
                List<TaskWebCrawlConfig> crawlConfigs = taskWebCrawlConfigService.listByTaskCode(taskCode);
                if (crawlConfigs.isEmpty()) {
                    throw new IllegalArgumentException("任务未配置网页爬取模块");
                }
                CrawlExecutionResults results = executeCrawlConfigs(task, crawlConfigs, params);
                reportFiles = results.getFiles();
                notifyFiles = results.getNotifyFiles();
                inlineResults = results.getInlineResults();
                chartFiles = results.getChartFiles();
            } else {
                List<TaskSqlConfig> sqlConfigs = taskSqlConfigService.listByTaskCode(taskCode);
                if (sqlConfigs.isEmpty()) {
                    throw new IllegalArgumentException("任务未配置 SQL 模块");
                }

                SqlExecutionResults results = executeSqlConfigs(task, sqlConfigs, params);
                reportFiles = results.getFiles();
                notifyFiles = results.getNotifyFiles();
                inlineResults = results.getInlineResults();
                chartFiles = results.getChartFiles();
            }
            StringBuilder resultMsg = new StringBuilder("生成 ")
                    .append(reportFiles.size()).append(" 个报表文件");
            if (!inlineResults.isEmpty()) {
                resultMsg.append("，").append(inlineResults.size()).append(" 个结果内联发送");
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
            publishTaskExecutionEvents(task, logEntity, reportFiles, notifyFiles, inlineResults, chartFiles);
        }
    }

    private void publishTaskExecutionEvents(TaskConfig task, TaskLog logEntity, List<File> reportFiles,
                                            List<File> notifyFiles, List<InlineResult> inlineResults,
                                            Map<String, File> chartFiles) {
        String status = logEntity.getStatus();
        if ("SUCCESS".equals(status)) {
            eventPublisher.publishEvent(new TaskExecutionEvent(this, task, logEntity, reportFiles, notifyFiles, inlineResults, chartFiles, TaskExecutionEvent.EventType.TASK_SUCCESS));
            eventPublisher.publishEvent(new TaskExecutionEvent(this, task, logEntity, reportFiles, notifyFiles, inlineResults, chartFiles, TaskExecutionEvent.EventType.TASK_COMPLETED));
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
                GeneratedFile generatedFile = processTemplateChain(task, template, group, params, results);
                results.addFile(generatedFile.outputFile(), generatedFile.notifyFile());
            } else {
                // 按 Excel 合并组拆分：同组合并输出，非同组保持独立
                Map<String, List<Integer>> excelMergeGroups = new LinkedHashMap<>();
                List<Integer> individualIndexes = new ArrayList<>();
                for (int i = 0; i < group.size(); i++) {
                    TaskSqlConfig sql = group.get(i);
                    if ("EXCEL".equalsIgnoreCase(sql.getOutputFormat()) && StringUtils.hasText(sql.getExcelMergeGroup())) {
                        excelMergeGroups.computeIfAbsent(sql.getExcelMergeGroup(), k -> new ArrayList<>()).add(i);
                    } else {
                        individualIndexes.add(i);
                    }
                }

                for (Map.Entry<String, List<Integer>> mergeEntry : excelMergeGroups.entrySet()) {
                    List<TaskSqlConfig> mergeSqls = new ArrayList<>();
                    List<List<Map<String, Object>>> mergeDataList = new ArrayList<>();
                    for (int idx : mergeEntry.getValue()) {
                        TaskSqlConfig sql = group.get(idx);
                        List<Map<String, Object>> data = executeSqlWithLoop(sql, params);
                        mergeSqls.add(sql);
                        mergeDataList.add(data);
                        File chartFile = generateChartFile(task, sql, data);
                        if (chartFile != null) {
                            results.addChartFile(sql.getSqlCode(), chartFile);
                        }
                    }
                    List<ExcelGenerationService.ExcelSheetSource> sources = new ArrayList<>();
                    for (int i = 0; i < mergeSqls.size(); i++) {
                        TaskSqlConfig sql = mergeSqls.get(i);
                        List<Map<String, Object>> data = mergeDataList.get(i);
                        if (data == null || data.isEmpty()) {
                            continue;
                        }
                        boolean hasSheetNameColumn = !data.isEmpty() && data.get(0).containsKey("_sheet_name");
                        if (hasSheetNameColumn) {
                            Map<String, List<Map<String, Object>>> subGroups = new LinkedHashMap<>();
                            for (Map<String, Object> row : data) {
                                Object sheetNameValue = row.get("_sheet_name");
                                String sheetName = sheetNameValue == null ? "" : sheetNameValue.toString();
                                subGroups.computeIfAbsent(sheetName, k -> new ArrayList<>()).add(row);
                            }
                            for (Map.Entry<String, List<Map<String, Object>>> subEntry : subGroups.entrySet()) {
                                sources.add(new ExcelGenerationService.ExcelSheetSource(subEntry.getKey(), stripSheetNameColumn(subEntry.getValue())));
                            }
                        } else {
                            String sheetName = StringUtils.hasText(sql.getExcelSheetName()) ? sql.getExcelSheetName() : sql.getSqlName();
                            sources.add(new ExcelGenerationService.ExcelSheetSource(sheetName, data));
                        }
                    }
                    String extension = resolveExtension("EXCEL", mergeSqls.get(0).getFileSuffix());
                    String outputPath = buildOutputPath(task, mergeSqls.get(0), extension);
                    File baseFile = null;
                    boolean updateExistingSheet = false;
                    int insertPosition = -1;
                    if (isAppendModeEnabled(mergeSqls.get(0))) {
                        baseFile = resolveBaseFile(mergeSqls.get(0));
                        updateExistingSheet = isUpdateSameSheetEnabled(mergeSqls.get(0));
                        insertPosition = getAppendPosition(mergeSqls.get(0));
                    }

                    if (baseFile != null) {
                        // 先按用户设定的文件名生成只包含本次新数据的文件，作为通知附件
                        String notifyFileName = Paths.get(outputPath).getFileName().toString();
                        String notifyOutputPath = buildTempOutputPath(task.getId(), extension, notifyFileName);
                        File notifyFile = excelGenerationService.generateMergedExcel(sources, notifyOutputPath, null, false, -1);
                        // 再追加到基础文件，作为最终归档文件
                        File outputFile = excelGenerationService.appendSheetsToBaseFile(baseFile, notifyFile, outputPath, updateExistingSheet, insertPosition);
                        results.addFile(outputFile, notifyFile);
                    } else {
                        results.addFile(excelGenerationService.generateMergedExcel(sources, outputPath, null, false, -1));
                    }
                }

                for (int idx : individualIndexes) {
                    TaskSqlConfig sql = group.get(idx);
                    List<Map<String, Object>> data = executeSqlWithLoop(sql, params);
                    if ("INLINE".equalsIgnoreCase(sql.getOutputFormat())) {
                        results.addInline(new InlineSqlResult(sql.getSqlName(), sql.getSqlCode(), data));
                    } else {
                        GeneratedFile generatedFile = generateSqlOutputFile(task, sql, data);
                        results.addFile(generatedFile.outputFile(), generatedFile.notifyFile());
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

    private List<Map<String, Object>> executeSqlWithLoop(TaskSqlConfig sqlConfig, Map<String, Object> params) throws Exception {
        Map<String, Object> mergedParams = mergeSqlParams(sqlConfig, params);
        if (!excelLoopHelper.isLoopEnabled(sqlConfig)) {
            return sqlExecutor.executeQuery(sqlConfig.getDatasourceId(), sqlConfig.getSqlContent(), mergedParams);
        }
        List<ExcelLoopHelper.LoopIterationResult> iterations = excelLoopHelper.expandLoop(sqlConfig, mergedParams);
        List<Map<String, Object>> combined = new ArrayList<>();
        for (ExcelLoopHelper.LoopIterationResult iteration : iterations) {
            combined.addAll(iteration.data());
        }
        return combined;
    }

    private record GeneratedFile(File outputFile, File notifyFile) {
    }

    private static class SqlExecutionResults {
        private final List<File> files = new ArrayList<>();
        private final List<File> notifyFiles = new ArrayList<>();
        private final List<InlineResult> inlineResults = new ArrayList<>();
        private final Map<String, File> chartFiles = new LinkedHashMap<>();

        public void addFile(File file) {
            addFile(file, file);
        }

        public void addFile(File outputFile, File notifyFile) {
            if (outputFile != null) {
                files.add(outputFile);
            }
            if (notifyFile != null) {
                notifyFiles.add(notifyFile);
            }
        }

        public void addInline(InlineResult inlineResult) {
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

        public List<File> getNotifyFiles() {
            return notifyFiles;
        }

        public List<InlineResult> getInlineResults() {
            return inlineResults;
        }

        public Map<String, File> getChartFiles() {
            return chartFiles;
        }
    }

    private CrawlExecutionResults executeCrawlConfigs(TaskConfig task, List<TaskWebCrawlConfig> crawlConfigs,
                                                      Map<String, Object> params) throws Exception {
        // 预分组：按模板编码分组
        Map<String, List<TaskWebCrawlConfig>> templateGroups = new LinkedHashMap<>();
        for (TaskWebCrawlConfig crawl : crawlConfigs) {
            if (StringUtils.hasText(crawl.getTemplateCode())) {
                templateGroups.computeIfAbsent(crawl.getTemplateCode(), k -> new ArrayList<>()).add(crawl);
            }
        }

        // 按原始顺序构建输出单元，模板单元聚合，单配置单元保持独立
        List<CrawlOutputEntry> entries = new ArrayList<>();
        Set<String> processedTemplateCodes = new HashSet<>();
        for (TaskWebCrawlConfig crawl : crawlConfigs) {
            if (StringUtils.hasText(crawl.getTemplateCode())) {
                String templateCode = crawl.getTemplateCode();
                if (!processedTemplateCodes.add(templateCode)) {
                    continue;
                }
                ReportTemplate template = reportTemplateService.getByCode(templateCode);
                if (template == null) {
                    throw new IllegalArgumentException("模板编码不存在: " + templateCode);
                }
                List<TaskWebCrawlConfig> group = templateGroups.get(templateCode);
                String extension = resolveExtension(template.getTemplateType(), group.get(0).getFileSuffix());
                String outputPath = buildOutputPath(task, group.get(0), extension);
                boolean excelMerge = "xlsx".equalsIgnoreCase(extension);
                entries.add(CrawlOutputEntry.template(template, group, outputPath, excelMerge));
            } else {
                String outputFormat = StringUtils.hasText(crawl.getOutputFormat()) ? crawl.getOutputFormat() : "CSV";
                String extension = resolveExtension(outputFormat, crawl.getFileSuffix());
                String outputPath = buildOutputPath(task, crawl, extension);
                boolean excelMerge = "EXCEL".equalsIgnoreCase(outputFormat);
                entries.add(CrawlOutputEntry.single(crawl, outputPath, excelMerge));
            }
        }

        CrawlExecutionResults results = new CrawlExecutionResults();
        Map<String, List<File>> excelMergeTempFiles = new LinkedHashMap<>();

        for (CrawlOutputEntry entry : entries) {
            if (entry.isExcelMerge()) {
                File tempFile;
                if (entry.isTemplate()) {
                    tempFile = new File(buildTempOutputPath(task.getId(), "xlsx"));
                    processTemplateChain(task, entry.getTemplate(), entry.getConfigs(), params, results, tempFile.getAbsolutePath());
                } else {
                    TaskWebCrawlConfig crawl = entry.getConfig();
                    WebCrawlResult crawlResult = webCrawlExecutor.execute(crawl, params);
                    List<Map<String, Object>> data = crawlResult.data();
                    String sheetName = StringUtils.hasText(crawl.getExcelSheetName())
                            ? crawl.getExcelSheetName() : crawl.getCrawlName();
                    log.info("网页爬取 Excel 临时输出: crawlCode={}, sheetName={}, dataRows={}",
                            crawl.getCrawlCode(), sheetName, data != null ? data.size() : 0);
                    tempFile = excelGenerationService.generateSingleExcel(data, buildTempOutputPath(task.getId(), "xlsx"), sheetName);
                    File chartFile = generateChartFile(task, crawl, data);
                    if (chartFile != null) {
                        results.addChartFile(crawl.getCrawlCode(), chartFile);
                    }
                    if (crawlResult.mediaFiles() != null && !crawlResult.mediaFiles().isEmpty()) {
                        crawlResult.mediaFiles().forEach(results::addFile);
                    }
                }
                entry.setTempFile(tempFile);
                excelMergeTempFiles.computeIfAbsent(entry.getOutputPath(), k -> new ArrayList<>()).add(tempFile);
            } else if (entry.isTemplate()) {
                results.addFile(processTemplateChain(task, entry.getTemplate(), entry.getConfigs(), params, results));
            } else {
                processSingleCrawlConfig(task, entry.getConfig(), params, results);
            }
        }

        // 按最终输出路径合并 Excel：同一路径下多个临时文件使用追加模式合并，单个则直接复制
        for (Map.Entry<String, List<File>> mergeEntry : excelMergeTempFiles.entrySet()) {
            String outputPath = mergeEntry.getKey();
            List<File> tempFiles = mergeEntry.getValue();
            File outputFile = new File(outputPath);
            try {
                if (tempFiles.size() == 1) {
                    Files.copy(tempFiles.get(0).toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("网页爬取 Excel 输出: outputPath={}, mode=copy", outputPath);
                } else {
                    // 每次任务执行都基于本次临时文件重新合并，不使用历史文件作为基础，
                    // 避免同名 sheet 被跳过导致新数据无法写入。
                    File baseFile = null;
                    for (File tempFile : tempFiles) {
                        baseFile = excelGenerationService.appendSheetsToBaseFile(baseFile, tempFile, outputPath, false, -1);
                    }
                    log.info("网页爬取 Excel 追加输出: outputPath={}, tempCount={}",
                            outputPath, tempFiles.size());
                }
                results.addFile(outputFile);
            } finally {
                for (File tempFile : tempFiles) {
                    Files.deleteIfExists(tempFile.toPath());
                }
            }
        }

        return results;
    }

    private void processSingleCrawlConfig(TaskConfig task, TaskWebCrawlConfig crawl,
                                          Map<String, Object> params, CrawlExecutionResults results) throws Exception {
        WebCrawlResult crawlResult = webCrawlExecutor.execute(crawl, params);
        List<Map<String, Object>> data = crawlResult.data();
        log.info("网页爬取单独输出: crawlCode={}, outputFormat={}, dataRows={}",
                crawl.getCrawlCode(), crawl.getOutputFormat(), data != null ? data.size() : 0);
        if ("INLINE".equalsIgnoreCase(crawl.getOutputFormat())) {
            results.addInline(new InlineCrawlResult(crawl.getCrawlName(), crawl.getCrawlCode(), data));
        } else {
            results.addFile(generateCrawlOutputFile(task, crawl, data));
        }
        File chartFile = generateChartFile(task, crawl, data);
        if (chartFile != null) {
            results.addChartFile(crawl.getCrawlCode(), chartFile);
        }
        if (crawlResult.mediaFiles() != null && !crawlResult.mediaFiles().isEmpty()) {
            crawlResult.mediaFiles().forEach(results::addFile);
        }
    }

    private static class CrawlExecutionResults {
        private final List<File> files = new ArrayList<>();
        private final List<File> notifyFiles = new ArrayList<>();
        private final List<InlineResult> inlineResults = new ArrayList<>();
        private final Map<String, File> chartFiles = new LinkedHashMap<>();

        public void addFile(File file) {
            addFile(file, file);
        }

        public void addFile(File outputFile, File notifyFile) {
            if (outputFile != null) {
                files.add(outputFile);
            }
            if (notifyFile != null) {
                notifyFiles.add(notifyFile);
            }
        }

        public void addInline(InlineResult inlineResult) {
            inlineResults.add(inlineResult);
        }

        public void addChartFile(String crawlCode, File chartFile) {
            if (chartFile != null) {
                chartFiles.put(crawlCode, chartFile);
            }
        }

        public List<File> getFiles() {
            return files;
        }

        public List<File> getNotifyFiles() {
            return notifyFiles;
        }

        public List<InlineResult> getInlineResults() {
            return inlineResults;
        }

        public Map<String, File> getChartFiles() {
            return chartFiles;
        }
    }

    private static class CrawlOutputEntry {
        private final boolean template;
        private final ReportTemplate templateObj;
        private final List<TaskWebCrawlConfig> configs;
        private final TaskWebCrawlConfig config;
        private final String outputPath;
        private final boolean excelMerge;
        private File tempFile;

        private CrawlOutputEntry(boolean template, ReportTemplate templateObj, List<TaskWebCrawlConfig> configs,
                                 TaskWebCrawlConfig config, String outputPath, boolean excelMerge) {
            this.template = template;
            this.templateObj = templateObj;
            this.configs = configs;
            this.config = config;
            this.outputPath = outputPath;
            this.excelMerge = excelMerge;
        }

        static CrawlOutputEntry template(ReportTemplate template, List<TaskWebCrawlConfig> configs,
                                         String outputPath, boolean excelMerge) {
            return new CrawlOutputEntry(true, template, configs, null, outputPath, excelMerge);
        }

        static CrawlOutputEntry single(TaskWebCrawlConfig config, String outputPath, boolean excelMerge) {
            return new CrawlOutputEntry(false, null, null, config, outputPath, excelMerge);
        }

        boolean isTemplate() {
            return template;
        }

        ReportTemplate getTemplate() {
            return templateObj;
        }

        List<TaskWebCrawlConfig> getConfigs() {
            return configs;
        }

        TaskWebCrawlConfig getConfig() {
            return config;
        }

        String getOutputPath() {
            return outputPath;
        }

        boolean isExcelMerge() {
            return excelMerge;
        }

        File getTempFile() {
            return tempFile;
        }

        void setTempFile(File tempFile) {
            this.tempFile = tempFile;
        }
    }

    private File processTemplateChain(TaskConfig task, ReportTemplate template, List<TaskWebCrawlConfig> crawlConfigs,
                                      Map<String, Object> params, CrawlExecutionResults results) throws Exception {
        String templateType = template.getTemplateType();
        String extension = resolveExtension(templateType, crawlConfigs.get(0).getFileSuffix());
        String outputFileName = buildOutputPath(task, crawlConfigs.get(0), extension);
        return processTemplateChain(task, template, crawlConfigs, params, results, outputFileName);
    }

    private File processTemplateChain(TaskConfig task, ReportTemplate template, List<TaskWebCrawlConfig> crawlConfigs,
                                      Map<String, Object> params, CrawlExecutionResults results,
                                      String outputFileName) throws Exception {
        String templateType = template.getTemplateType();
        TemplateProcessor processor = templateProcessorFactory.getProcessor(templateType);
        File templateFile = resolveTemplateFile(template.getFilePath());

        File currentFile = templateFile;
        File previousTempFile = null;
        for (int i = 0; i < crawlConfigs.size(); i++) {
            TaskWebCrawlConfig crawl = crawlConfigs.get(i);
            WebCrawlResult crawlResult = webCrawlExecutor.execute(crawl, params);
            List<Map<String, Object>> data = crawlResult.data();
            File chartFile = generateChartFile(task, crawl, data);
            if (chartFile != null) {
                results.addChartFile(crawl.getCrawlCode(), chartFile);
            }
            boolean isLast = i == crawlConfigs.size() - 1;
            String stepOutput = isLast ? outputFileName : buildTempOutputPath(task.getId(), templateType, i);
            Map<String, Object> context = buildProcessorContext(crawl, chartFile);
            currentFile = processor.process(currentFile, data, stepOutput, isLast, context);
            if (crawlResult.mediaFiles() != null && !crawlResult.mediaFiles().isEmpty()) {
                crawlResult.mediaFiles().forEach(results::addFile);
            }
            if (previousTempFile != null) {
                Files.deleteIfExists(previousTempFile.toPath());
            }
            previousTempFile = isLast ? null : currentFile;
        }
        return new File(outputFileName);
    }

    private File generateCrawlOutputFile(TaskConfig task, TaskWebCrawlConfig crawlConfig,
                                         List<Map<String, Object>> data) throws Exception {
        String outputFormat = StringUtils.hasText(crawlConfig.getOutputFormat()) ? crawlConfig.getOutputFormat() : "CSV";
        String upperFormat = outputFormat.toUpperCase();
        String extension = resolveExtension(upperFormat, crawlConfig.getFileSuffix());
        String outputPath = buildOutputPath(task, crawlConfig, extension);

        log.info("生成爬取输出文件: crawlCode={}, format={}, path={}, 数据行数={}",
                crawlConfig.getCrawlCode(), upperFormat, outputPath, data != null ? data.size() : 0);
        if (log.isDebugEnabled() && data != null && !data.isEmpty()) {
            int previewSize = Math.min(3, data.size());
            try {
                log.debug("爬取输出数据预览: {}",
                        objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(data.subList(0, previewSize)));
            } catch (Exception e) {
                log.debug("爬取输出数据预览: {}", data.subList(0, previewSize));
            }
        }

        return switch (upperFormat) {
            case "CSV" -> templateProcessorFactory.getProcessor("CSV")
                    .process(createTempCsvTemplate(data), data, outputPath);
            case "EXCEL" -> {
                String sheetName = StringUtils.hasText(crawlConfig.getExcelSheetName())
                        ? crawlConfig.getExcelSheetName() : crawlConfig.getCrawlName();
                yield excelGenerationService.generateSingleExcel(data, outputPath, sheetName);
            }
            case "TXT" -> {
                File templateFile = createTempTemplate(upperFormat, data);
                yield templateProcessorFactory.getProcessor(upperFormat)
                        .process(templateFile, data, outputPath, true);
            }
            default -> {
                String csvPath = buildOutputPath(task, crawlConfig, resolveExtension("CSV", null));
                yield templateProcessorFactory.getProcessor("CSV")
                        .process(createTempCsvTemplate(data), data, csvPath);
            }
        };
    }

    private File generateChartFile(TaskConfig task, TaskWebCrawlConfig crawlConfig, List<Map<String, Object>> data) {
        if (crawlConfig.getChartEnabled() == null || crawlConfig.getChartEnabled() != 1) {
            return null;
        }
        if (data == null || data.isEmpty()) {
            log.warn("爬取 {} 启用图表但结果为空，跳过生成", crawlConfig.getCrawlCode());
            return null;
        }
        String chartType = StringUtils.hasText(crawlConfig.getChartType()) ? crawlConfig.getChartType() : "BAR";
        String title = StringUtils.hasText(crawlConfig.getChartTitle()) ? crawlConfig.getChartTitle() : crawlConfig.getCrawlName();
        boolean autoMerge = crawlConfig.getChartAutoMerge() == null || crawlConfig.getChartAutoMerge() == 1;
        String labelRotation = StringUtils.hasText(crawlConfig.getChartLabelRotation()) ? crawlConfig.getChartLabelRotation() : "AUTO";
        File chartFile = chartGenerationService.generateChart(data, chartType, title, autoMerge, labelRotation, crawlConfig.getChartBackgroundColor());
        if (chartFile == null) {
            log.warn("爬取 {} 图表生成失败", crawlConfig.getCrawlCode());
        } else {
            log.info("爬取 {} 图表生成成功: {}", crawlConfig.getCrawlCode(), chartFile.getAbsolutePath());
        }
        return chartFile;
    }

    private Map<String, Object> buildProcessorContext(TaskWebCrawlConfig crawl, File chartFile) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("crawlId", crawl.getId());
        context.put("crawlCode", crawl.getCrawlCode());
        context.put("crawlName", crawl.getCrawlName());
        context.put("excelSheetName", crawl.getExcelSheetName());
        context.put("chartEnabled", crawl.getChartEnabled());
        context.put("chartType", crawl.getChartType());
        context.put("chartTitle", crawl.getChartTitle());
        context.put("chartBackgroundColor", crawl.getChartBackgroundColor());
        context.put("chartFile", chartFile);
        return context;
    }

    private GeneratedFile processTemplateChain(TaskConfig task, ReportTemplate template, List<TaskSqlConfig> sqlConfigs,
                                               Map<String, Object> params, SqlExecutionResults results) throws Exception {
        String templateType = template.getTemplateType();
        TemplateProcessor processor = templateProcessorFactory.getProcessor(templateType);
        File templateFile = resolveTemplateFile(template.getFilePath());
        String extension = resolveExtension(templateType, sqlConfigs.get(0).getFileSuffix());
        String outputFileName = buildOutputPath(task, sqlConfigs.get(0), extension);
        File baseFile = null;
        if (isAppendModeEnabled(sqlConfigs.get(0))) {
            baseFile = resolveBaseFile(sqlConfigs.get(0));
        }

        File currentFile = templateFile;
        File previousTempFile = null;
        for (int i = 0; i < sqlConfigs.size(); i++) {
            TaskSqlConfig sql = sqlConfigs.get(i);
            List<Map<String, Object>> data = executeSqlWithLoop(sql, params);
            File chartFile = generateChartFile(task, sql, data);
            if (chartFile != null) {
                results.addChartFile(sql.getSqlCode(), chartFile);
            }
            boolean isLast = i == sqlConfigs.size() - 1;
            // 追加模式下，最后一步也写入临时文件，避免模板处理器覆盖基础文件，
            // 真正的合并由 appendSheetsToBaseFile 完成。通知附件使用配置的文件名。
            String stepOutput;
            if (isLast && baseFile != null) {
                String notifyFileName = Paths.get(outputFileName).getFileName().toString();
                stepOutput = buildTempOutputPath(task.getId(), extension, notifyFileName);
            } else if (isLast) {
                stepOutput = outputFileName;
            } else {
                stepOutput = buildTempOutputPath(task.getId(), templateType, i);
            }
            Map<String, Object> context = buildProcessorContext(sql, chartFile);
            currentFile = processor.process(currentFile, data, stepOutput, isLast, context);
            if (previousTempFile != null) {
                Files.deleteIfExists(previousTempFile.toPath());
            }
            previousTempFile = isLast ? null : currentFile;
        }
        File outputFile = new File(outputFileName);
        if (baseFile != null) {
            boolean updateExistingSheet = isUpdateSameSheetEnabled(sqlConfigs.get(0));
            excelGenerationService.appendSheetsToBaseFile(baseFile, currentFile, outputFileName, updateExistingSheet, getAppendPosition(sqlConfigs.get(0)));
            // 追加模式下，通知使用本次生成的临时文件，不删除
            return new GeneratedFile(outputFile, currentFile);
        }
        return new GeneratedFile(outputFile, outputFile);
    }

    private Map<String, Object> buildProcessorContext(TaskSqlConfig sql, File chartFile) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("sqlId", sql.getId());
        context.put("sqlCode", sql.getSqlCode());
        context.put("sqlName", sql.getSqlName());
        context.put("excelSheetName", sql.getExcelSheetName());
        context.put("chartEnabled", sql.getChartEnabled());
        context.put("chartType", sql.getChartType());
        context.put("chartTitle", sql.getChartTitle());
        context.put("chartBackgroundColor", sql.getChartBackgroundColor());
        context.put("chartFile", chartFile);
        return context;
    }

    private List<Map<String, Object>> stripSheetNameColumn(List<Map<String, Object>> data) {
        List<Map<String, Object>> result = new ArrayList<>(data.size());
        for (Map<String, Object> row : data) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.remove("_sheet_name");
            result.add(copy);
        }
        return result;
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

    private boolean isAppendModeEnabled(TaskSqlConfig sqlConfig) {
        return sqlConfig.getExcelAppendMode() != null && sqlConfig.getExcelAppendMode() == 1;
    }

    private boolean isUpdateSameSheetEnabled(TaskSqlConfig sqlConfig) {
        return sqlConfig.getExcelAppendUpdateSameSheet() != null && sqlConfig.getExcelAppendUpdateSameSheet() == 1;
    }

    private int getAppendPosition(TaskSqlConfig sqlConfig) {
        Integer position = sqlConfig.getExcelAppendPosition();
        return position != null ? position : -1;
    }

    private File resolveBaseFile(TaskSqlConfig sqlConfig) {
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
        File chartFile = chartGenerationService.generateChart(data, chartType, title, autoMerge, labelRotation, sqlConfig.getChartBackgroundColor());
        if (chartFile == null) {
            log.warn("SQL {} 图表生成失败", sqlConfig.getSqlCode());
        } else {
            log.info("SQL {} 图表生成成功: {}", sqlConfig.getSqlCode(), chartFile.getAbsolutePath());
        }
        return chartFile;
    }

    private GeneratedFile generateSqlOutputFile(TaskConfig task, TaskSqlConfig sqlConfig, List<Map<String, Object>> data) throws Exception {
        String outputFormat = StringUtils.hasText(sqlConfig.getOutputFormat()) ? sqlConfig.getOutputFormat() : "CSV";
        String upperFormat = outputFormat.toUpperCase();
        String extension = resolveExtension(upperFormat, sqlConfig.getFileSuffix());
        String outputPath = buildOutputPath(task, sqlConfig, extension);

        if (!isAppendModeEnabled(sqlConfig)) {
            File outputFile = generateSqlOutputToPath(task, sqlConfig, data, outputPath, null, false, -1);
            return new GeneratedFile(outputFile, outputFile);
        }

        File baseFile = resolveBaseFile(sqlConfig);
        String baseFilePath = baseFile != null ? baseFile.getAbsolutePath() : null;

        // 先按用户设定的文件名生成只包含本次新数据的文件，作为通知附件
        String notifyFileName = Paths.get(outputPath).getFileName().toString();
        String notifyOutputPath = buildTempOutputPath(task.getId(), extension, notifyFileName);
        File notifyFile = generateSqlOutputToPath(task, sqlConfig, data, notifyOutputPath, null, false, -1);

        // 再把本次新数据追加到基础文件，作为最终归档文件
        if (baseFile != null) {
            boolean updateExistingSheet = isUpdateSameSheetEnabled(sqlConfig);
            int insertPosition = getAppendPosition(sqlConfig);
            excelGenerationService.appendSheetsToBaseFile(baseFile, notifyFile, outputPath, updateExistingSheet, insertPosition);
        }

        return new GeneratedFile(new File(outputPath), notifyFile);
    }

    private File generateSqlOutputToPath(TaskConfig task, TaskSqlConfig sqlConfig, List<Map<String, Object>> data,
                                         String outputPath, String baseFilePath, boolean updateExistingSheet,
                                         int insertPosition) throws Exception {
        String outputFormat = StringUtils.hasText(sqlConfig.getOutputFormat()) ? sqlConfig.getOutputFormat() : "CSV";
        String upperFormat = outputFormat.toUpperCase();

        return switch (upperFormat) {
            case "CSV" -> templateProcessorFactory.getProcessor("CSV")
                    .process(createTempCsvTemplate(data), data, outputPath);
            case "EXCEL" -> {
                if (data != null && !data.isEmpty() && data.get(0).containsKey("_sheet_name")) {
                    Map<String, List<Map<String, Object>>> subGroups = new LinkedHashMap<>();
                    for (Map<String, Object> row : data) {
                        Object sheetNameValue = row.get("_sheet_name");
                        String sheetName = sheetNameValue == null ? "" : sheetNameValue.toString();
                        subGroups.computeIfAbsent(sheetName, k -> new ArrayList<>()).add(row);
                    }
                    List<ExcelGenerationService.ExcelSheetSource> sources = new ArrayList<>();
                    for (Map.Entry<String, List<Map<String, Object>>> subEntry : subGroups.entrySet()) {
                        sources.add(new ExcelGenerationService.ExcelSheetSource(subEntry.getKey(), stripSheetNameColumn(subEntry.getValue())));
                    }
                    yield excelGenerationService.generateMergedExcel(sources, outputPath, baseFilePath, updateExistingSheet, insertPosition);
                } else {
                    String sheetName = StringUtils.hasText(sqlConfig.getExcelSheetName()) ? sqlConfig.getExcelSheetName() : sqlConfig.getSqlName();
                    yield excelGenerationService.generateSingleExcel(data, outputPath, sheetName, baseFilePath, updateExistingSheet, insertPosition);
                }
            }
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

    private String buildTempOutputPath(Long taskId, String extension) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(taskId), "temp");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String ext = StringUtils.hasText(extension) ? extension : "tmp";
        String name = "temp_" + System.currentTimeMillis() + "." + ext;
        return outputDir.resolve(name).toString();
    }

    private String buildTempOutputPath(Long taskId, String extension, String fileName) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(taskId), "temp");
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        if (!StringUtils.hasText(fileName)) {
            return buildTempOutputPath(taskId, extension);
        }
        return outputDir.resolve(fileName).toString();
    }

    private String buildOutputPath(TaskConfig task, TaskWebCrawlConfig crawlConfig, String extension) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(task.getId()));
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String fileName = buildFileName(task, crawlConfig);
        fileName = ensureExtension(fileName, extension);
        return outputDir.resolve(fileName).toString();
    }

    private String buildFileName(TaskConfig task, TaskWebCrawlConfig crawlConfig) {
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
