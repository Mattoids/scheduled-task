package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.task.SqlExecutor;
import com.mattoid.scheduled.template.TemplateProcessor;
import com.mattoid.scheduled.template.TemplateProcessorFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TaskExecutionService {

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

    public TaskExecutionService(TaskConfigMapper taskConfigMapper,
                                TaskLogMapper taskLogMapper,
                                SqlExecutor sqlExecutor,
                                DatasourceConfigService datasourceConfigService,
                                EmailConfigService emailConfigService,
                                EmailRecipientService emailRecipientService,
                                ReportTemplateService reportTemplateService,
                                TaskSqlConfigService taskSqlConfigService,
                                TemplateProcessorFactory templateProcessorFactory,
                                EmailSenderService emailSenderService) {
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
    }

    @Transactional(rollbackFor = Exception.class)
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

        try {
            List<TaskSqlConfig> sqlConfigs = taskSqlConfigService.listByTaskId(taskId);
            if (sqlConfigs.isEmpty()) {
                throw new IllegalArgumentException("任务未配置 SQL 模块");
            }

            List<File> reportFiles = executeSqlConfigs(task, sqlConfigs);
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
        }
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
        String outputFileName = buildOutputPath(task, sqlConfigs.get(0), extension);

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
        // 无模板时 POI 格式无法生成有效文件，回退为 CSV
        if ("EXCEL".equals(upperFormat) || "WORD".equals(upperFormat) || "PPT".equals(upperFormat)) {
            upperFormat = "CSV";
        }
        String extension = resolveExtension(upperFormat, sqlConfig.getFileSuffix());
        String outputPath = buildOutputPath(task, sqlConfig, extension);

        if ("CSV".equals(upperFormat)) {
            return templateProcessorFactory.getProcessor("CSV")
                    .process(createTempCsvTemplate(data), data, outputPath);
        }

        // TXT 格式：构造一个带占位符的临时模板
        File templateFile = createTempTemplate(upperFormat, data);
        return templateProcessorFactory.getProcessor(upperFormat)
                .process(templateFile, data, outputPath, true);
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
            case "EXCEL", "WORD", "PPT" -> {
                // POI 格式无法简单构造空模板，这里创建一个空文件，处理器需要能够处理
            }
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
        String pattern = sqlConfig != null && StringUtils.hasText(sqlConfig.getFileNamePattern())
                ? sqlConfig.getFileNamePattern()
                : task.getFileNamePattern();
        if (!StringUtils.hasText(pattern)) {
            pattern = "report_{yyyyMMddHHmmss}";
        }
        LocalDateTime now = LocalDateTime.now();
        String name = pattern
                .replace("{yyyyMMddHHmmss}", now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
                .replace("{yyyyMMdd}", now.format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                .replace("{yyyy-MM-dd}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .replace("{HHmmss}", now.format(DateTimeFormatter.ofPattern("HHmmss")));
        return name.replaceAll("[^a-zA-Z0-9_\\-\\.\\{\\}]", "_");
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

    private void sendReportEmail(TaskConfig task, List<File> reportFiles) throws Exception {
        EmailConfig emailConfig = emailConfigService.getById(task.getEmailConfigId());
        if (emailConfig == null) {
            throw new IllegalArgumentException("发件邮箱配置不存在: " + task.getEmailConfigId());
        }

        List<String> toList = resolveRecipients(task);
        if (toList.isEmpty()) {
            throw new IllegalArgumentException("收件人列表为空");
        }

        String subject = task.getEmailSubject() != null ? task.getEmailSubject() : "定时报表";
        String body = task.getEmailBody() != null ? task.getEmailBody() : "请查收附件报表。";
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
