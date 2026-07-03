package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.task.SqlExecutor;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.service.wecom.WeComBotClient;
import com.mattoid.scheduled.template.TemplateProcessor;
import com.mattoid.scheduled.template.TemplateProcessorFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TaskExecutionService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");
    private static final Pattern UNSAFE_FILENAME_CHAR_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");

    @Value("${report.upload.path}")
    private String uploadPath;

    private final TaskConfigMapper taskConfigMapper;
    private final TaskLogMapper taskLogMapper;
    private final SqlExecutor sqlExecutor;
    private final DatasourceConfigService datasourceConfigService;
    private final EmailConfigService emailConfigService;
    private final EmailRecipientService emailRecipientService;
    private final ReportTemplateService reportTemplateService;
    private final TaskSqlConfigService taskSqlConfigService;
    private final TemplateProcessorFactory templateProcessorFactory;
    private final EmailSenderService emailSenderService;
    private final WeComAppConfigService weComAppConfigService;
    private final WeComBotConfigService weComBotConfigService;
    private final WeComAppManager weComAppManager;
    private final WeComBotClient weComBotClient;

    public TaskExecutionService(TaskConfigMapper taskConfigMapper,
                                TaskLogMapper taskLogMapper,
                                SqlExecutor sqlExecutor,
                                DatasourceConfigService datasourceConfigService,
                                EmailConfigService emailConfigService,
                                EmailRecipientService emailRecipientService,
                                ReportTemplateService reportTemplateService,
                                TaskSqlConfigService taskSqlConfigService,
                                TemplateProcessorFactory templateProcessorFactory,
                                EmailSenderService emailSenderService,
                                WeComAppConfigService weComAppConfigService,
                                WeComBotConfigService weComBotConfigService,
                                WeComAppManager weComAppManager,
                                WeComBotClient weComBotClient) {
        this.taskConfigMapper = taskConfigMapper;
        this.taskLogMapper = taskLogMapper;
        this.sqlExecutor = sqlExecutor;
        this.datasourceConfigService = datasourceConfigService;
        this.emailConfigService = emailConfigService;
        this.emailRecipientService = emailRecipientService;
        this.reportTemplateService = reportTemplateService;
        this.taskSqlConfigService = taskSqlConfigService;
        this.templateProcessorFactory = templateProcessorFactory;
        this.emailSenderService = emailSenderService;
        this.weComAppConfigService = weComAppConfigService;
        this.weComBotConfigService = weComBotConfigService;
        this.weComAppManager = weComAppManager;
        this.weComBotClient = weComBotClient;
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
        try {
            List<TaskSqlConfig> sqlConfigs = taskSqlConfigService.listByTaskId(taskId);
            if (sqlConfigs.isEmpty()) {
                throw new IllegalArgumentException("任务未配置 SQL 模块");
            }

            reportFiles = executeSqlConfigs(task, sqlConfigs);
            logEntity.setResultMessage("生成 " + reportFiles.size() + " 个报表文件");

            if (!reportFiles.isEmpty()) {
                List<String> filePaths = reportFiles.stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.toList());
                logEntity.setFilePath(String.join(",", filePaths));
                sendReportEmail(task, reportFiles);
            }

            logEntity.setStatus("SUCCESS");
        } catch (Exception e) {
            log.error("任务执行失败: {}", taskId, e);
            logEntity.setStatus("FAILED");
            logEntity.setErrorMessage(e.getMessage());
        } finally {
            logEntity.setEndTime(LocalDateTime.now());
            taskLogMapper.updateById(logEntity);
            sendWeComNotification(task, logEntity, reportFiles);
        }
    }

    @Async
    public void executeTaskAsync(Long taskId, String triggerMode) {
        executeTask(taskId, triggerMode);
    }

    private List<File> executeSqlConfigs(TaskConfig task, List<TaskSqlConfig> sqlConfigs) throws Exception {
        // 按 template_id 分组，保持原始顺序
        Map<Object, List<TaskSqlConfig>> groups = new LinkedHashMap<>();
        for (TaskSqlConfig sql : sqlConfigs) {
            Object key = sql.getTemplateId() != null ? sql.getTemplateId() : "sql_" + sql.getId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(sql);
        }

        List<File> result = new ArrayList<>();
        for (Map.Entry<Object, List<TaskSqlConfig>> entry : groups.entrySet()) {
            List<TaskSqlConfig> group = entry.getValue();
            if (group.get(0).getTemplateId() != null) {
                Long templateId = group.get(0).getTemplateId();
                ReportTemplate template = reportTemplateService.getById(templateId);
                if (template == null) {
                    throw new IllegalArgumentException("模板不存在: " + templateId);
                }
                result.add(processTemplateChain(task, template, group));
            } else {
                for (TaskSqlConfig sql : group) {
                    List<Map<String, Object>> data = sqlExecutor.executeQuery(sql.getDatasourceId(), sql.getSqlContent());
                    result.add(generateSqlOutputFile(task, sql, data));
                }
            }
        }
        return result;
    }

    private File processTemplateChain(TaskConfig task, ReportTemplate template, List<TaskSqlConfig> sqlConfigs) throws Exception {
        String templateType = template.getTemplateType();
        TemplateProcessor processor = templateProcessorFactory.getProcessor(templateType);
        File templateFile = new File(template.getFilePath());
        String extension = resolveExtension(templateType, sqlConfigs.get(0).getFileSuffix());
        String outputFileName = buildOutputPath(task, sqlConfigs.get(0), extension, true);

        File currentFile = templateFile;
        File previousTempFile = null;
        for (int i = 0; i < sqlConfigs.size(); i++) {
            TaskSqlConfig sql = sqlConfigs.get(i);
            List<Map<String, Object>> data = sqlExecutor.executeQuery(sql.getDatasourceId(), sql.getSqlContent());
            boolean isLast = i == sqlConfigs.size() - 1;
            // 中间步骤写入临时文件，最后一步写入最终文件
            String stepOutput = isLast ? outputFileName : buildTempOutputPath(task.getId(), templateType, i);
            currentFile = processor.process(currentFile, data, stepOutput, isLast);
            if (previousTempFile != null) {
                Files.deleteIfExists(previousTempFile.toPath());
            }
            previousTempFile = isLast ? null : currentFile;
        }
        return new File(outputFileName);
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
                // WORD/PPT 无模板时无法生成有效文件，回退为 CSV
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
        return buildOutputPath(task, sqlConfig, extension, false);
    }

    private String buildOutputPath(TaskConfig task, TaskSqlConfig sqlConfig, String extension, boolean preferTaskPattern) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(task.getId()));
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String fileName = buildFileName(task, sqlConfig, preferTaskPattern);
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

    private String buildFileName(TaskConfig task, TaskSqlConfig sqlConfig, boolean preferTaskPattern) {
        String pattern;
        if (preferTaskPattern && StringUtils.hasText(task.getFileNamePattern())) {
            pattern = task.getFileNamePattern();
        } else if (sqlConfig != null && StringUtils.hasText(sqlConfig.getFileNamePattern())) {
            pattern = sqlConfig.getFileNamePattern();
        } else if (StringUtils.hasText(task.getFileNamePattern())) {
            pattern = task.getFileNamePattern();
        } else {
            pattern = "report_{yyyyMMddHHmmss}";
        }
        String fileName = replacePlaceholders(pattern);
        // 只替换文件系统保留字符，保留中文、空格等
        return UNSAFE_FILENAME_CHAR_PATTERN.matcher(fileName).replaceAll("_");
    }

    private String replacePlaceholders(String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return pattern;
        }
        LocalDateTime now = LocalDateTime.now();
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(pattern);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement;
            if ("lastMonth".equals(placeholder)) {
                replacement = lastMonth.format(DateTimeFormatter.ofPattern("MM"));
            } else if (placeholder.startsWith("lastMonth:")) {
                String format = placeholder.substring("lastMonth:".length());
                replacement = lastMonth.format(DateTimeFormatter.ofPattern(format));
            } else if ("nextMonth".equals(placeholder)) {
                replacement = nextMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));
            } else if (placeholder.startsWith("nextMonth:")) {
                String format = placeholder.substring("nextMonth:".length());
                replacement = nextMonth.format(DateTimeFormatter.ofPattern(format));
            } else {
                try {
                    replacement = now.format(DateTimeFormatter.ofPattern(placeholder));
                } catch (IllegalArgumentException e) {
                    // 不是合法日期格式则保留原占位符
                    replacement = matcher.group(0);
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String ensureExtension(String fileName, String extension) {
        String lower = fileName.toLowerCase();
        String dotExt = "." + extension.toLowerCase();
        if (lower.endsWith(dotExt)) {
            return fileName;
        }
        // 如果文件名已经有其他扩展名，追加新扩展名
        return fileName + dotExt;
    }

    private void sendWeComNotification(TaskConfig task, TaskLog logEntity, List<File> reportFiles) {
        if (task.getWeComAppConfigId() == null && task.getWeComBotConfigId() == null) {
            return;
        }
        String summary = buildWeComSummary(task, logEntity);

        if (task.getWeComAppConfigId() != null) {
            WeComAppConfig appConfig = weComAppConfigService.getById(task.getWeComAppConfigId());
            if (appConfig == null || appConfig.getStatus() == null || appConfig.getStatus() != 1) {
                log.warn("企业微信应用配置不可用: {}", task.getWeComAppConfigId());
            } else {
                String toUser = StringUtils.hasText(task.getWeComToUser()) ? task.getWeComToUser() : "@all";
                try {
                    weComAppManager.sendText(task.getWeComAppConfigId(), toUser, summary);
                    for (File file : reportFiles) {
                        weComAppManager.sendFile(task.getWeComAppConfigId(), toUser, file);
                    }
                } catch (Exception e) {
                    log.error("企业微信应用通知发送失败: taskId={}", task.getId(), e);
                }
            }
        }

        if (task.getWeComBotConfigId() != null) {
            WeComBotConfig botConfig = weComBotConfigService.getById(task.getWeComBotConfigId());
            if (botConfig == null || botConfig.getStatus() == null || botConfig.getStatus() != 1) {
                log.warn("企业微信群机器人配置不可用: {}", task.getWeComBotConfigId());
            } else {
                try {
                    weComBotClient.sendText(botConfig.getWebhookKey(), summary);
                    for (File file : reportFiles) {
                        weComBotClient.sendFile(botConfig.getWebhookKey(), file);
                    }
                } catch (Exception e) {
                    log.error("企业微信群机器人通知发送失败: taskId={}", task.getId(), e);
                }
            }
        }
    }

    private String buildWeComSummary(TaskConfig task, TaskLog logEntity) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务执行通知\n");
        sb.append("任务: ").append(task.getTaskName()).append("\n");
        sb.append("状态: ").append(logEntity.getStatus()).append("\n");
        if (logEntity.getStartTime() != null && logEntity.getEndTime() != null) {
            long seconds = java.time.Duration.between(logEntity.getStartTime(), logEntity.getEndTime()).getSeconds();
            sb.append("耗时: ").append(seconds).append("s\n");
        }
        if (StringUtils.hasText(logEntity.getResultMessage())) {
            sb.append("结果: ").append(logEntity.getResultMessage()).append("\n");
        }
        if (StringUtils.hasText(logEntity.getErrorMessage())) {
            sb.append("错误: ").append(logEntity.getErrorMessage()).append("\n");
        }
        if (StringUtils.hasText(logEntity.getFilePath())) {
            sb.append("文件: ").append(logEntity.getFilePath());
        }
        return sb.toString();
    }

    private void sendReportEmail(TaskConfig task, List<File> reportFiles) throws Exception {
        EmailConfig emailConfig = emailConfigService.getById(task.getEmailConfigId());
        if (emailConfig == null) {
            throw new IllegalArgumentException("发件邮箱配置不存在: " + task.getEmailConfigId());
        }

        List<String> toList = resolveRecipients(task);
        if (toList.isEmpty()) {
            throw new IllegalArgumentException("收件人列表为空");
        }

        String subject = task.getEmailSubject() != null ? replacePlaceholders(task.getEmailSubject()) : "定时报表";
        String body = task.getEmailBody() != null ? replacePlaceholders(task.getEmailBody()) : "请查收附件报表。";
        emailSenderService.sendEmail(emailConfig, toList, subject, body, reportFiles);
    }

    private List<String> resolveRecipients(TaskConfig task) {
        Set<String> emails = new LinkedHashSet<>();

        List<EmailRecipient> individualRecipients = emailRecipientService.listByIds(task.getRecipientIds());
        for (EmailRecipient recipient : individualRecipients) {
            if (StringUtils.hasText(recipient.getEmail())) {
                emails.add(recipient.getEmail());
            }
        }

        List<EmailRecipient> groupRecipients = emailRecipientService.listByGroupIds(task.getRecipientGroupIds());
        for (EmailRecipient recipient : groupRecipients) {
            if (StringUtils.hasText(recipient.getEmail())) {
                emails.add(recipient.getEmail());
            }
        }

        return new ArrayList<>(emails);
    }
}
