package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.task.SqlExecutor;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final TemplateProcessorFactory templateProcessorFactory;
    private final EmailSenderService emailSenderService;

    public TaskExecutionService(TaskConfigMapper taskConfigMapper,
                                TaskLogMapper taskLogMapper,
                                SqlExecutor sqlExecutor,
                                DatasourceConfigService datasourceConfigService,
                                EmailConfigService emailConfigService,
                                EmailRecipientService emailRecipientService,
                                ReportTemplateService reportTemplateService,
                                TemplateProcessorFactory templateProcessorFactory,
                                EmailSenderService emailSenderService) {
        this.taskConfigMapper = taskConfigMapper;
        this.taskLogMapper = taskLogMapper;
        this.sqlExecutor = sqlExecutor;
        this.datasourceConfigService = datasourceConfigService;
        this.emailConfigService = emailConfigService;
        this.emailRecipientService = emailRecipientService;
        this.reportTemplateService = reportTemplateService;
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
            List<Map<String, Object>> data = sqlExecutor.executeQuery(task.getDatasourceId(), task.getSqlContent());
            logEntity.setResultMessage("查询返回 " + data.size() + " 条记录");

            File reportFile = generateReportFile(task, data);
            logEntity.setFilePath(reportFile.getAbsolutePath());

            sendReportEmail(task, reportFile);

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

    private File generateReportFile(TaskConfig task, List<Map<String, Object>> data) throws Exception {
        String fileName = buildFileName(task);
        Path outputDir = Paths.get(uploadPath, "reports", String.valueOf(task.getId()));
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String outputPath = outputDir.resolve(fileName).toString();

        if (task.getTemplateId() != null) {
            ReportTemplate template = reportTemplateService.getById(task.getTemplateId());
            if (template == null) {
                throw new IllegalArgumentException("模板不存在: " + task.getTemplateId());
            }
            return templateProcessorFactory.getProcessor(template.getTemplateType())
                    .process(new File(template.getFilePath()), data, outputPath);
        }

        // 无模板时生成 CSV
        if (!outputPath.toLowerCase().endsWith(".csv")) {
            outputPath += ".csv";
        }
        return templateProcessorFactory.getProcessor("CSV")
                .process(createTempCsvTemplate(data), data, outputPath);
    }

    private File createTempCsvTemplate(List<Map<String, Object>> data) throws Exception {
        Path temp = Files.createTempFile("template", ".csv");
        if (!data.isEmpty()) {
            String header = String.join(",", data.get(0).keySet());
            Files.writeString(temp, header + "\n");
        }
        return temp.toFile();
    }

    private void sendReportEmail(TaskConfig task, File reportFile) throws Exception {
        EmailConfig emailConfig = emailConfigService.getById(task.getEmailConfigId());
        if (emailConfig == null) {
            throw new IllegalArgumentException("发件邮箱配置不存在: " + task.getEmailConfigId());
        }

        List<EmailRecipient> recipients = emailRecipientService.listByIds(task.getRecipientIds());
        List<String> toList = recipients.stream()
                .map(EmailRecipient::getEmail)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (toList.isEmpty()) {
            throw new IllegalArgumentException("收件人列表为空");
        }

        String subject = task.getEmailSubject() != null ? task.getEmailSubject() : "定时报表";
        String body = task.getEmailBody() != null ? task.getEmailBody() : "请查收附件报表。";
        emailSenderService.sendEmail(emailConfig, toList, subject, body, List.of(reportFile));
    }

    private String buildFileName(TaskConfig task) {
        String pattern = task.getFileNamePattern();
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
}
