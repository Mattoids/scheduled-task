package com.mattoid.scheduled.task;

import com.mattoid.scheduled.entity.ReportTemplate;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.event.InlineCrawlResult;
import com.mattoid.scheduled.service.ChartGenerationService;
import com.mattoid.scheduled.service.ExcelGenerationService;
import com.mattoid.scheduled.service.ReportAssembler;
import com.mattoid.scheduled.service.ReportTemplateService;
import com.mattoid.scheduled.service.TaskWebCrawlConfigService;
import com.mattoid.scheduled.template.TemplateProcessor;
import com.mattoid.scheduled.template.TemplateProcessorFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 网页爬取类型任务处理器。
 */
@Slf4j
@Component
public class CrawlTaskHandler implements TaskHandler {

    private final TaskWebCrawlConfigService taskWebCrawlConfigService;
    private final WebCrawlExecutor webCrawlExecutor;
    private final ExcelGenerationService excelGenerationService;
    private final ChartGenerationService chartGenerationService;
    private final TemplateProcessorFactory templateProcessorFactory;
    private final ReportAssembler reportAssembler;
    private final ReportTemplateService reportTemplateService;

    public CrawlTaskHandler(TaskWebCrawlConfigService taskWebCrawlConfigService,
                            WebCrawlExecutor webCrawlExecutor,
                            ExcelGenerationService excelGenerationService,
                            ChartGenerationService chartGenerationService,
                            TemplateProcessorFactory templateProcessorFactory,
                            ReportAssembler reportAssembler,
                            ReportTemplateService reportTemplateService) {
        this.taskWebCrawlConfigService = taskWebCrawlConfigService;
        this.webCrawlExecutor = webCrawlExecutor;
        this.excelGenerationService = excelGenerationService;
        this.chartGenerationService = chartGenerationService;
        this.templateProcessorFactory = templateProcessorFactory;
        this.reportAssembler = reportAssembler;
        this.reportTemplateService = reportTemplateService;
    }

    @Override
    public boolean supports(TaskConfig task) {
        return task != null && "CRAWL".equalsIgnoreCase(task.getTaskType());
    }

    @Override
    public TaskExecutionResult handle(TaskConfig task, Map<String, Object> params) throws Exception {
        List<TaskWebCrawlConfig> crawlConfigs = taskWebCrawlConfigService.listByTaskCode(task.getTaskCode());
        if (crawlConfigs.isEmpty()) {
            throw new IllegalArgumentException("任务未配置网页爬取模块");
        }
        return executeCrawlConfigs(task, crawlConfigs, params);
    }

    private TaskExecutionResult executeCrawlConfigs(TaskConfig task, List<TaskWebCrawlConfig> crawlConfigs,
                                                    Map<String, Object> params) throws Exception {
        Map<String, List<TaskWebCrawlConfig>> templateGroups = new LinkedHashMap<>();
        for (TaskWebCrawlConfig crawl : crawlConfigs) {
            if (StringUtils.hasText(crawl.getTemplateCode())) {
                templateGroups.computeIfAbsent(crawl.getTemplateCode(), k -> new ArrayList<>()).add(crawl);
            }
        }

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
                String extension = reportAssembler.resolveExtension(template.getTemplateType(), group.get(0).getFileSuffix());
                String outputPath = reportAssembler.buildOutputPath(task, group.get(0), extension);
                boolean excelMerge = "xlsx".equalsIgnoreCase(extension);
                entries.add(CrawlOutputEntry.template(template, group, outputPath, excelMerge));
            } else {
                String outputFormat = StringUtils.hasText(crawl.getOutputFormat()) ? crawl.getOutputFormat() : "CSV";
                String extension = reportAssembler.resolveExtension(outputFormat, crawl.getFileSuffix());
                String outputPath = reportAssembler.buildOutputPath(task, crawl, extension);
                boolean excelMerge = "EXCEL".equalsIgnoreCase(outputFormat);
                entries.add(CrawlOutputEntry.single(crawl, outputPath, excelMerge));
            }
        }

        TaskExecutionResult result = new TaskExecutionResult();
        Map<String, List<File>> excelMergeTempFiles = new LinkedHashMap<>();

        for (CrawlOutputEntry entry : entries) {
            if (entry.isExcelMerge()) {
                File tempFile;
                if (entry.isTemplate()) {
                    tempFile = new File(reportAssembler.buildTempOutputPath(task.getId(), "xlsx"));
                    processTemplateChain(task, entry.getTemplate(), entry.getConfigs(), params, result, tempFile.getAbsolutePath());
                } else {
                    TaskWebCrawlConfig crawl = entry.getConfig();
                    WebCrawlResult crawlResult = webCrawlExecutor.execute(crawl, params);
                    List<Map<String, Object>> data = crawlResult.data();
                    String sheetName = StringUtils.hasText(crawl.getExcelSheetName())
                            ? crawl.getExcelSheetName() : crawl.getCrawlName();
                    log.info("网页爬取 Excel 临时输出: crawlCode={}, sheetName={}, dataRows={}",
                            crawl.getCrawlCode(), sheetName, data != null ? data.size() : 0);
                    tempFile = excelGenerationService.generateSingleExcel(data, reportAssembler.buildTempOutputPath(task.getId(), "xlsx"), sheetName);
                    File chartFile = generateChartFile(task, crawl, data);
                    if (chartFile != null) {
                        result.addChartFile(crawl.getCrawlCode(), chartFile);
                    }
                    if (crawlResult.mediaFiles() != null && !crawlResult.mediaFiles().isEmpty()) {
                        crawlResult.mediaFiles().forEach(result::addFile);
                    }
                }
                entry.setTempFile(tempFile);
                excelMergeTempFiles.computeIfAbsent(entry.getOutputPath(), k -> new ArrayList<>()).add(tempFile);
            } else if (entry.isTemplate()) {
                result.addFile(processTemplateChain(task, entry.getTemplate(), entry.getConfigs(), params, result));
            } else {
                processSingleCrawlConfig(task, entry.getConfig(), params, result);
            }
        }

        for (Map.Entry<String, List<File>> mergeEntry : excelMergeTempFiles.entrySet()) {
            String outputPath = mergeEntry.getKey();
            List<File> tempFiles = mergeEntry.getValue();
            File outputFile = new File(outputPath);
            try {
                if (tempFiles.size() == 1) {
                    Files.copy(tempFiles.get(0).toPath(), outputFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log.info("网页爬取 Excel 输出: outputPath={}, mode=copy", outputPath);
                } else {
                    File baseFile = null;
                    for (File tempFile : tempFiles) {
                        baseFile = excelGenerationService.appendSheetsToBaseFile(baseFile, tempFile, outputPath, false, -1);
                    }
                    log.info("网页爬取 Excel 追加输出: outputPath={}, tempCount={}",
                            outputPath, tempFiles.size());
                }
                result.addFile(outputFile);
            } finally {
                for (File tempFile : tempFiles) {
                    Files.deleteIfExists(tempFile.toPath());
                }
            }
        }

        return result;
    }

    private void processSingleCrawlConfig(TaskConfig task, TaskWebCrawlConfig crawl,
                                          Map<String, Object> params, TaskExecutionResult result) throws Exception {
        WebCrawlResult crawlResult = webCrawlExecutor.execute(crawl, params);
        List<Map<String, Object>> data = crawlResult.data();
        log.info("网页爬取单独输出: crawlCode={}, outputFormat={}, dataRows={}",
                crawl.getCrawlCode(), crawl.getOutputFormat(), data != null ? data.size() : 0);
        if ("INLINE".equalsIgnoreCase(crawl.getOutputFormat())) {
            result.addInline(new InlineCrawlResult(crawl.getCrawlName(), crawl.getCrawlCode(), data));
        } else {
            result.addFile(generateCrawlOutputFile(task, crawl, data));
        }
        File chartFile = generateChartFile(task, crawl, data);
        if (chartFile != null) {
            result.addChartFile(crawl.getCrawlCode(), chartFile);
        }
        if (crawlResult.mediaFiles() != null && !crawlResult.mediaFiles().isEmpty()) {
            crawlResult.mediaFiles().forEach(result::addFile);
        }
    }

    private File processTemplateChain(TaskConfig task, ReportTemplate template, List<TaskWebCrawlConfig> crawlConfigs,
                                      Map<String, Object> params, TaskExecutionResult result) throws Exception {
        String templateType = template.getTemplateType();
        String extension = reportAssembler.resolveExtension(templateType, crawlConfigs.get(0).getFileSuffix());
        String outputFileName = reportAssembler.buildOutputPath(task, crawlConfigs.get(0), extension);
        return processTemplateChain(task, template, crawlConfigs, params, result, outputFileName);
    }

    private File processTemplateChain(TaskConfig task, ReportTemplate template, List<TaskWebCrawlConfig> crawlConfigs,
                                      Map<String, Object> params, TaskExecutionResult result,
                                      String outputFileName) throws Exception {
        String templateType = template.getTemplateType();
        TemplateProcessor processor = templateProcessorFactory.getProcessor(templateType);
        File templateFile = reportAssembler.resolveTemplateFile(template.getFilePath());

        File currentFile = templateFile;
        File previousTempFile = null;
        for (int i = 0; i < crawlConfigs.size(); i++) {
            TaskWebCrawlConfig crawl = crawlConfigs.get(i);
            WebCrawlResult crawlResult = webCrawlExecutor.execute(crawl, params);
            List<Map<String, Object>> data = crawlResult.data();
            File chartFile = generateChartFile(task, crawl, data);
            if (chartFile != null) {
                result.addChartFile(crawl.getCrawlCode(), chartFile);
            }
            boolean isLast = i == crawlConfigs.size() - 1;
            String stepOutput = isLast ? outputFileName : reportAssembler.buildTempOutputPath(task.getId(), templateType, i);
            Map<String, Object> context = buildProcessorContext(crawl, chartFile);
            currentFile = processor.process(currentFile, data, stepOutput, isLast, context);
            if (crawlResult.mediaFiles() != null && !crawlResult.mediaFiles().isEmpty()) {
                crawlResult.mediaFiles().forEach(result::addFile);
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
        String extension = reportAssembler.resolveExtension(upperFormat, crawlConfig.getFileSuffix());
        String outputPath = reportAssembler.buildOutputPath(task, crawlConfig, extension);

        log.info("生成爬取输出文件: crawlCode={}, format={}, path={}, 数据行数={}",
                crawlConfig.getCrawlCode(), upperFormat, outputPath, data != null ? data.size() : 0);

        return switch (upperFormat) {
            case "CSV" -> templateProcessorFactory.getProcessor("CSV")
                    .process(reportAssembler.createTempCsvTemplate(data), data, outputPath);
            case "EXCEL" -> {
                String sheetName = StringUtils.hasText(crawlConfig.getExcelSheetName())
                        ? crawlConfig.getExcelSheetName() : crawlConfig.getCrawlName();
                yield excelGenerationService.generateSingleExcel(data, outputPath, sheetName);
            }
            case "TXT" -> {
                File templateFile = reportAssembler.createTempTemplate(upperFormat, data);
                yield templateProcessorFactory.getProcessor(upperFormat)
                        .process(templateFile, data, outputPath, true);
            }
            default -> {
                String csvPath = reportAssembler.buildOutputPath(task, crawlConfig, reportAssembler.resolveExtension("CSV", null));
                yield templateProcessorFactory.getProcessor("CSV")
                        .process(reportAssembler.createTempCsvTemplate(data), data, csvPath);
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
        File chartFile = chartGenerationService.generateChart(data, chartType, title, autoMerge, labelRotation,
                crawlConfig.getChartBackgroundColor(), crawlConfig.getChartFontFamily(), crawlConfig.getChartFontSize());
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
        context.put("chartFontFamily", crawl.getChartFontFamily());
        context.put("chartFontSize", crawl.getChartFontSize());
        context.put("chartFile", chartFile);
        return context;
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
}
