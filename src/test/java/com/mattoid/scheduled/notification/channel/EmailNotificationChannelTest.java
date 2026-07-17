package com.mattoid.scheduled.notification.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mattoid.scheduled.entity.EmailConfig;
import com.mattoid.scheduled.entity.EmailRecipient;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.NotificationRule;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.event.TaskExecutionEvent;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.service.EmailRecipientService;
import com.mattoid.scheduled.service.EmailSenderService;
import com.mattoid.scheduled.service.NotificationConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationChannelTest {

    @Mock
    private NotificationConfigService notificationConfigService;
    @Mock
    private EmailRecipientService emailRecipientService;
    @Mock
    private EmailSenderService emailSenderService;

    @InjectMocks
    private EmailNotificationChannel channel;

    private NotificationContext context(NotificationRule rule, NotificationConfig config) {
        TaskConfig task = new TaskConfig();
        task.setId(1L);
        task.setTaskName("测试任务");
        task.setTaskCode("TEST_TASK");
        task.setTriggerType("CRON");

        TaskLog log = new TaskLog();
        log.setStatus("SUCCESS");
        log.setStartTime(LocalDateTime.now());
        log.setEndTime(LocalDateTime.now());

        TaskExecutionEvent event = new TaskExecutionEvent(this, task, log, Collections.emptyList(),
                TaskExecutionEvent.EventType.TASK_COMPLETED);
        return new NotificationContext(event, rule, config);
    }

    private NotificationConfig config(Long id, String configJson) throws JsonProcessingException {
        NotificationConfig cfg = new NotificationConfig();
        cfg.setId(id);
        cfg.setConfigType("EMAIL");
        cfg.setConfigJson(configJson);
        cfg.setStatus(1);
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSmtpHost("smtp.example.com");
        emailConfig.setSmtpPort(587);
        emailConfig.setUsername("user");
        emailConfig.setFromAddress("from@example.com");
        when(notificationConfigService.parseConfigJson(configJson, EmailConfig.class))
                .thenReturn(emailConfig);
        return cfg;
    }

    @Test
    void shouldSendEmailWithResolvedRecipients() throws Exception {
        NotificationRule rule = new NotificationRule();
        rule.setId(1L);
        rule.setChannel("EMAIL");
        rule.setConfigId(1L);
        rule.setRecipientIds("1");

        String configJson = "{\"smtpHost\":\"smtp.example.com\",\"smtpPort\":587,\"username\":\"user\",\"fromAddress\":\"from@example.com\"}";
        NotificationConfig cfg = config(1L, configJson);

        EmailRecipient recipient = new EmailRecipient();
        recipient.setEmail("test@example.com");
        when(emailRecipientService.listByIds("1")).thenReturn(List.of(recipient));

        channel.send(context(rule, cfg));

        verify(emailSenderService, times(1)).sendEmail(
                any(EmailConfig.class),
                argThat((List<String> list) -> list.contains("test@example.com")),
                anyString(),
                anyString(),
                anyList(),
                anyMap());
    }
}
