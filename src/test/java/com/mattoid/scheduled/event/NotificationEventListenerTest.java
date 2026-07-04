package com.mattoid.scheduled.event;

import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.service.*;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.service.wecom.WeComBotClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationRuleService notificationRuleService;
    @Mock
    private NotificationConfigService notificationConfigService;
    @Mock
    private EmailRecipientService emailRecipientService;
    @Mock
    private EmailSenderService emailSenderService;
    @Mock
    private WeComAppManager weComAppManager;
    @Mock
    private WeComBotClient weComBotClient;
    @Mock
    private AiAssistantService aiAssistantService;

    @InjectMocks
    private NotificationEventListener listener;

    private TaskExecutionEvent event(TaskExecutionEvent.EventType eventType) {
        TaskConfig task = new TaskConfig();
        task.setId(1L);
        task.setTaskName("测试任务");
        task.setTaskCode("TEST_TASK");
        task.setTriggerType("CRON");

        TaskLog log = new TaskLog();
        log.setStatus("SUCCESS");
        log.setStartTime(LocalDateTime.now());
        log.setEndTime(LocalDateTime.now());

        return new TaskExecutionEvent(this, task, log, Collections.emptyList(), eventType);
    }

    private NotificationConfig config(Long id, String configType, String configJson) {
        NotificationConfig cfg = new NotificationConfig();
        cfg.setId(id);
        cfg.setConfigName("测试配置");
        cfg.setConfigType(configType);
        cfg.setConfigJson(configJson);
        cfg.setStatus(1);
        return cfg;
    }

    @Test
    void emailRuleShouldSendEmail() throws Exception {
        NotificationRule rule = new NotificationRule();
        rule.setId(1L);
        rule.setEventType("TASK_COMPLETED");
        rule.setChannel("EMAIL");
        rule.setConfigId(1L);
        rule.setRecipientIds("1");
        rule.setEnabled(1);

        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_COMPLETED", 1L))
                .thenReturn(List.of(rule));

        String configJson = "{\"smtpHost\":\"smtp.example.com\",\"smtpPort\":587,\"username\":\"user\",\"fromAddress\":\"from@example.com\"}";
        when(notificationConfigService.getById(1L))
                .thenReturn(config(1L, "EMAIL", configJson));

        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSmtpHost("smtp.example.com");
        emailConfig.setSmtpPort(587);
        emailConfig.setUsername("user");
        emailConfig.setFromAddress("from@example.com");
        when(notificationConfigService.parseConfigJson(configJson, EmailConfig.class))
                .thenReturn(emailConfig);

        EmailRecipient recipient = new EmailRecipient();
        recipient.setEmail("test@example.com");
        when(emailRecipientService.listByIds("1")).thenReturn(List.of(recipient));

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_COMPLETED));

        verify(emailSenderService, times(1)).sendEmail(eq(emailConfig), anyList(), anyString(), anyString(), anyList());
    }

    @Test
    void wecomAppRuleShouldSendMessage() throws Exception {
        NotificationRule rule = new NotificationRule();
        rule.setId(2L);
        rule.setEventType("TASK_COMPLETED");
        rule.setChannel("WECOM_APP");
        rule.setConfigId(2L);
        rule.setWecomToUser("user1");
        rule.setEnabled(1);

        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_COMPLETED", 1L))
                .thenReturn(List.of(rule));

        String configJson = "{\"corpId\":\"corp\",\"agentId\":1}";
        when(notificationConfigService.getById(2L))
                .thenReturn(config(2L, "WECOM_APP", configJson));

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_COMPLETED));

        verify(weComAppManager, times(1)).sendText(eq(2L), eq("user1"), anyString());
    }

    @Test
    void wecomBotRuleShouldSendMessage() throws Exception {
        NotificationRule rule = new NotificationRule();
        rule.setId(3L);
        rule.setEventType("TASK_COMPLETED");
        rule.setChannel("WECOM_BOT");
        rule.setConfigId(3L);
        rule.setEnabled(1);

        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_COMPLETED", 1L))
                .thenReturn(List.of(rule));

        String configJson = "{\"webhookKey\":\"key123\"}";
        when(notificationConfigService.getById(3L))
                .thenReturn(config(3L, "WECOM_BOT", configJson));

        WeComBotConfig botConfig = new WeComBotConfig();
        botConfig.setWebhookKey("key123");
        when(notificationConfigService.parseConfigJson(configJson, WeComBotConfig.class))
                .thenReturn(botConfig);

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_COMPLETED));

        verify(weComBotClient, times(1)).sendText(eq("key123"), anyString(), eq(Collections.emptyList()));
    }

    @Test
    void disabledRuleShouldBeSkipped() {
        NotificationRule rule = new NotificationRule();
        rule.setId(4L);
        rule.setEventType("TASK_COMPLETED");
        rule.setChannel("EMAIL");
        rule.setEnabled(0);

        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_COMPLETED", 1L))
                .thenReturn(Collections.emptyList());

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_COMPLETED));

        verifyNoInteractions(emailSenderService);
    }

    @Test
    void multipleRulesShouldAllBeDispatched() throws Exception {
        NotificationRule emailRule = new NotificationRule();
        emailRule.setId(5L);
        emailRule.setEventType("TASK_SUCCESS");
        emailRule.setChannel("EMAIL");
        emailRule.setConfigId(1L);
        emailRule.setRecipientIds("1");
        emailRule.setEnabled(1);

        NotificationRule botRule = new NotificationRule();
        botRule.setId(6L);
        botRule.setEventType("TASK_SUCCESS");
        botRule.setChannel("WECOM_BOT");
        botRule.setConfigId(3L);
        botRule.setEnabled(1);

        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_SUCCESS", 1L))
                .thenReturn(List.of(emailRule, botRule));

        String emailJson = "{\"smtpHost\":\"smtp.example.com\",\"smtpPort\":587,\"username\":\"user\",\"fromAddress\":\"from@example.com\"}";
        when(notificationConfigService.getById(1L))
                .thenReturn(config(1L, "EMAIL", emailJson));
        EmailConfig emailConfig = new EmailConfig();
        emailConfig.setSmtpHost("smtp.example.com");
        emailConfig.setSmtpPort(587);
        emailConfig.setUsername("user");
        emailConfig.setFromAddress("from@example.com");
        when(notificationConfigService.parseConfigJson(emailJson, EmailConfig.class))
                .thenReturn(emailConfig);

        EmailRecipient recipient = new EmailRecipient();
        recipient.setEmail("test@example.com");
        when(emailRecipientService.listByIds("1")).thenReturn(List.of(recipient));

        String botJson = "{\"webhookKey\":\"key123\"}";
        when(notificationConfigService.getById(3L))
                .thenReturn(config(3L, "WECOM_BOT", botJson));
        WeComBotConfig botConfig = new WeComBotConfig();
        botConfig.setWebhookKey("key123");
        when(notificationConfigService.parseConfigJson(botJson, WeComBotConfig.class))
                .thenReturn(botConfig);

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_SUCCESS));

        verify(emailSenderService, times(1)).sendEmail(any(), anyList(), anyString(), anyString(), anyList());
        verify(weComBotClient, times(1)).sendText(eq("key123"), anyString(), eq(Collections.emptyList()));
    }
}
