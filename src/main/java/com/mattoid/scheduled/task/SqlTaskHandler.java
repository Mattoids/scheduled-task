package com.mattoid.scheduled.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.ReportTemplate;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.event.InlineResult;
import com.mattoid.scheduled.event.InlineSqlResult;
import com.mattoid.scheduled.service.ChartGenerationService;
import com.mattoid.scheduled.service.ExcelGenerationService;
import com.mattoid.scheduled.service.ExcelLoopHelper;
import com.mattoid.scheduled.service.ReportAssembler;
import com.mattoid.scheduled.service.ReportTemplateService;
import com.mattoid.scheduled.service.TaskSqlConfigService;
import com.mattoid.scheduled.template.TemplateProcessor;
import com.mattoid.scheduled.template.TemplateProcessorFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 类型任务处理器。
 */
@Slf4j
@Component
public class SqlTaskHandler implements TaskHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SqlExecutor sqlExecutor;
    private final ReportTemplateService reportTemplateService;
    private final TaskSqlConfigService taskSqlConfigService;
    private final ExcelLoopHelper excelLoopHelper;
    private final ExcelGenerationService excelGenerationService;
    private final ChartGenerationService chartGenerationService;
    private final TemplateProcessorFactory templateProcessorFactory;
    private final ReportAssembler reportAssembler;

    public SqlTaskHandler(SqlExecutor sqlExecutor,
                          ReportTemplateService reportTemplateService,
                          TaskSqlConfigService taskSqlConfigService,
                          ExcelLoopHelper excelLoopHelper,
                          ExcelGenerationService excelGenerationService,
                          ChartGenerationService chartGenerationService,
                          TemplateProcessorFactory templateProcessorFactory,
                          ReportAssembler reportAssembler) {
        this.sqlExecutor = sqlExecutor;
        this.reportTemplateService = reportTemplateService;
        this.taskSqlConfigService = taskSqlConfigService;
        this.excelLoopHelper = excelLoopHelper;
        this.excelGenerationService = excelGenerationService;
        this.chartGenerationService = chartGenerationService;
        this.templateProcessorFactory = templateProcessorFactory;
        this.reportAssembler = reportAssembler;
    }

    @Override
    public boolean supports(TaskConfig task) {
        return task != null && !"CRAWL".equalsIgnoreCase(task.getTaskType());
    }

    @Override
    public TaskExecutionResult handle(TaskConfig task, Map<String, Object> params) throws Exception {
        List<TaskSqlConfig> sqlConfigs = taskSqlConfigService.listByTaskCode(task.getTaskCode());
        if (sqlConfigs.isEmpty()) {
            throw new IllegalArgumentException("任务未配置 SQL 模块");
        }
        return executeSqlConfigs(task, sqlConfigs, params);
    }

    private TaskExecutionResult executeSqlConfigs(TaskConfig task, List<TaskSqlConfig> sqlConfigs, Map<String, Object> params) throws Exception {
        Map<String, List<TaskSqlConfig>> groups = new LinkedHashMap<>();
        for (TaskSqlConfig sql : sqlConfigs) {
            String key = StringUtils.hasText(sql.getTemplateCode()) ? sql.getTemplateCode() : "sql_" + sql.getId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(sql);
        }

        TaskExecutionResult result = new TaskExecutionResult();
        for (Map.Entry<String, List<TaskSqlConfig>> entry : groups.entrySet()) {
            List<TaskSqlConfig> group = entry.getValue();
            if (StringUtils.hasText(group.get(0).getTemplateCode())) {
                String templateCode = group.get(0).getTemplateCode();
                ReportTemplate template = reportTemplateService.getByCode(templateCode);
                if (template == null) {
                    throw new IllegalArgumentException("模板编码不存在: " + templateCode);
                }
                GeneratedFile generatedFile = processTemplateChain(task, template, group, params, result);
                result.addFile(generatedFile.outputFile(), generatedFile.notifyFile());
            } else {
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
                            result.addChartFile(sql.getSqlCode(), chartFile);
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
                    String extension = reportAssembler.resolveExtension("EXCEL", mergeSqls.get(0).getFileSuffix());
                    String outputPath = reportAssembler.buildOutputPath(task, mergeSqls.get(0), extension);
                    File baseFile = null;
                    boolean updateExistingSheet = false;
                    int insertPosition = -1;
                    if (isAppendModeEnabled(mergeSqls.get(0))) {
                        baseFile = reportAssembler.resolveBaseFile(mergeSqls.get(0));
                        updateExistingSheet = isUpdateSameSheetEnabled(mergeSqls.get(0));
                        insertPosition = getAppendPosition(mergeSqls.get(0));
                    }

                    if (baseFile != null) {
                        String notifyFileName = Paths.get(outputPath).getFileName().toString();
                        String notifyOutputPath = reportAssembler.buildTempOutputPath(task.getId(), extension, notifyFileName);
                        File notifyFile = excelGenerationService.generateMergedExcel(sources, notifyOutputPath, null, false, -1);
                        File outputFile = excelGenerationService.appendSheetsToBaseFile(baseFile, notifyFile, outputPath, updateExistingSheet, insertPosition);
                        result.addFile(outputFile, notifyFile);
                    } else {
                        result.addFile(excelGenerationService.generateMergedExcel(sources, outputPath, null, false, -1));
                    }
                }

                for (int idx : individualIndexes) {
                    TaskSqlConfig sql = group.get(idx);
                    List<Map<String, Object>> data = executeSqlWithLoop(sql, params);
                    if ("INLINE".equalsIgnoreCase(sql.getOutputFormat())) {
                        result.addInline(new InlineSqlResult(sql.getSqlName(), sql.getSqlCode(), data));
                    } else {
                        GeneratedFile generatedFile = generateSqlOutputFile(task, sql, data);
                        result.addFile(generatedFile.outputFile(), generatedFile.notifyFile());
                    }
                    File chartFile = generateChartFile(task, sql, data);
                    if (chartFile != null) {
                        result.addChartFile(sql.getSqlCode(), chartFile);
                    }
                }
            }
        }
        return result;
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

    private List<Map<String, Object>> stripSheetNameColumn(List<Map<String, Object>> data) {
        List<Map<String, Object>> result = new ArrayList<>(data.size());
        for (Map<String, Object> row : data) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.remove("_sheet_name");
            result.add(copy);
        }
        return result;
    }

    private record GeneratedFile(File outputFile, File notifyFile) {
    }

    private GeneratedFile processTemplateChain(TaskConfig task, ReportTemplate template, List<TaskSqlConfig> sqlConfigs,
                                               Map<String, Object> params, TaskExecutionResult result) throws Exception {
        String templateType = template.getTemplateType();
        TemplateProcessor processor = templateProcessorFactory.getProcessor(templateType);
        File templateFile = reportAssembler.resolveTemplateFile(template.getFilePath());
        String extension = reportAssembler.resolveExtension(templateType, sqlConfigs.get(0).getFileSuffix());
        String outputFileName = reportAssembler.buildOutputPath(task, sqlConfigs.get(0), extension);
        File baseFile = null;
        if (isAppendModeEnabled(sqlConfigs.get(0))) {
            baseFile = reportAssembler.resolveBaseFile(sqlConfigs.get(0));
        }

        File currentFile = templateFile;
        File previousTempFile = null;
        for (int i = 0; i < sqlConfigs.size(); i++) {
            TaskSqlConfig sql = sqlConfigs.get(i);
            List<Map<String, Object>> data = executeSqlWithLoop(sql, params);
            File chartFile = generateChartFile(task, sql, data);
            if (chartFile != null) {
                result.addChartFile(sql.getSqlCode(), chartFile);
            }
            boolean isLast = i == sqlConfigs.size() - 1;
            String stepOutput;
            if (isLast && baseFile != null) {
                String notifyFileName = Paths.get(outputFileName).getFileName().toString();
                stepOutput = reportAssembler.buildTempOutputPath(task.getId(), extension, notifyFileName);
            } else if (isLast) {
                stepOutput = outputFileName;
            } else {
                stepOutput = reportAssembler.buildTempOutputPath(task.getId(), templateType, i);
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
        context.put("chartFontFamily", sql.getChartFontFamily());
        context.put("chartFontSize", sql.getChartFontSize());
        context.put("chartFile", chartFile);
        return context;
    }

    private File generateChartFile(TaskConfig task, TaskSqlConfig sqlConfig, List<Map<String, Object>> data) {
        if (sqlConfig.getChartEnabled() == null || sqlConfig.getChartEnabled() != 1) {
            return null;
        }
        if (data == null || data.isEmpty()) {
            return null;
        }
        String chartType = StringUtils.hasText(sqlConfig.getChartType()) ? sqlConfig.getChartType() : "BAR";
        String title = StringUtils.hasText(sqlConfig.getChartTitle()) ? sqlConfig.getChartTitle() : sqlConfig.getSqlName();
        boolean autoMerge = sqlConfig.getChartAutoMerge() == null || sqlConfig.getChartAutoMerge() == 1;
        String labelRotation = StringUtils.hasText(sqlConfig.getChartLabelRotation()) ? sqlConfig.getChartLabelRotation() : "AUTO";
        return chartGenerationService.generateChart(data, chartType, title, autoMerge, labelRotation,
                sqlConfig.getChartBackgroundColor(), sqlConfig.getChartFontFamily(), sqlConfig.getChartFontSize());
    }

    private GeneratedFile generateSqlOutputFile(TaskConfig task, TaskSqlConfig sqlConfig, List<Map<String, Object>> data) throws Exception {
        String outputFormat = StringUtils.hasText(sqlConfig.getOutputFormat()) ? sqlConfig.getOutputFormat() : "CSV";
        String upperFormat = outputFormat.toUpperCase();
        String extension = reportAssembler.resolveExtension(upperFormat, sqlConfig.getFileSuffix());
        String outputPath = reportAssembler.buildOutputPath(task, sqlConfig, extension);

        if (!isAppendModeEnabled(sqlConfig)) {
            File outputFile = generateSqlOutputToPath(task, sqlConfig, data, outputPath, null, false, -1);
            return new GeneratedFile(outputFile, outputFile);
        }

        File baseFile = reportAssembler.resolveBaseFile(sqlConfig);
        String baseFilePath = baseFile != null ? baseFile.getAbsolutePath() : null;

        String notifyFileName = Paths.get(outputPath).getFileName().toString();
        String notifyOutputPath = reportAssembler.buildTempOutputPath(task.getId(), extension, notifyFileName);
        File notifyFile = generateSqlOutputToPath(task, sqlConfig, data, notifyOutputPath, null, false, -1);

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
                    .process(reportAssembler.createTempCsvTemplate(data), data, outputPath);
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
                File templateFile = reportAssembler.createTempTemplate(upperFormat, data);
                yield templateProcessorFactory.getProcessor(upperFormat)
                        .process(templateFile, data, outputPath, true);
            }
            default -> {
                String csvPath = reportAssembler.buildOutputPath(task, sqlConfig, reportAssembler.resolveExtension("CSV", null));
                yield templateProcessorFactory.getProcessor("CSV")
                        .process(reportAssembler.createTempCsvTemplate(data), data, csvPath);
            }
        };
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
}
