package com.mattoid.scheduled.event;

import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.NotificationRule;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.notification.NotificationChannel;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.service.AiAssistantService;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.NotificationLogService;
import com.mattoid.scheduled.service.NotificationRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationRuleService notificationRuleService;
    @Mock
    private NotificationConfigService notificationConfigService;
    @Mock
    private NotificationLogService notificationLogService;
    @Mock
    private AiAssistantService aiAssistantService;
    @Mock
    private NotificationChannel emailChannel;
    @Mock
    private NotificationChannel wecomAppChannel;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        when(emailChannel.channelType()).thenReturn("EMAIL");
        when(wecomAppChannel.channelType()).thenReturn("WECOM_APP");
        listener = new NotificationEventListener(
                notificationRuleService,
                notificationConfigService,
                notificationLogService,
                aiAssistantService,
                List.of(emailChannel, wecomAppChannel));
    }

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
    void emailRuleShouldDispatchToEmailChannel() throws Exception {
        NotificationRule rule = new NotificationRule();
        rule.setId(1L);
        rule.setEventType("TASK_COMPLETED");
        rule.setChannel("EMAIL");
        rule.setConfigId(1L);
        rule.setRecipientIds("1");
        rule.setEnabled(1);

        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_COMPLETED", "TEST_TASK"))
                .thenReturn(List.of(rule));
        when(notificationConfigService.getById(1L))
                .thenReturn(config(1L, "EMAIL", "{}"));
        when(emailChannel.resolveRecipient(any(NotificationContext.class)))
                .thenReturn("1");

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_COMPLETED));

        ArgumentCaptor<NotificationContext> captor = ArgumentCaptor.forClass(NotificationContext.class);
        verify(emailChannel, times(1)).send(captor.capture());
        NotificationContext ctx = captor.getValue();
        assertEquals("EMAIL", ctx.getRule().getChannel());
        assertEquals(1L, ctx.getConfig().getId());
        assertNull(ctx.getSubject());
        assertNull(ctx.getBody());
    }

    @Test
    void wecomAppRuleShouldDispatchToWecomAppChannel() throws Exception {
        NotificationRule rule = new NotificationRule();
        rule.setId(2L);
        rule.setEventType("TASK_COMPLETED");
        rule.setChannel("WECOM_APP");
        rule.setConfigId(2L);
        rule.setWecomToUser("user1");
        rule.setEnabled(1);

        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_COMPLETED", "TEST_TASK"))
                .thenReturn(List.of(rule));
        when(notificationConfigService.getById(2L))
                .thenReturn(config(2L, "WECOM_APP", "{}"));

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_COMPLETED));

        verify(wecomAppChannel, times(1)).send(any(NotificationContext.class));
    }

    @Test
    void disabledRuleShouldBeSkipped() throws Exception {
        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_COMPLETED", "TEST_TASK"))
                .thenReturn(Collections.emptyList());

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_COMPLETED));

        verify(emailChannel, never()).send(any(NotificationContext.class));
        verify(wecomAppChannel, never()).send(any(NotificationContext.class));
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

        NotificationRule appRule = new NotificationRule();
        appRule.setId(6L);
        appRule.setEventType("TASK_SUCCESS");
        appRule.setChannel("WECOM_APP");
        appRule.setConfigId(2L);
        appRule.setEnabled(1);

        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_SUCCESS", "TEST_TASK"))
                .thenReturn(List.of(emailRule, appRule));
        when(notificationConfigService.getById(1L))
                .thenReturn(config(1L, "EMAIL", "{}"));
        when(notificationConfigService.getById(2L))
                .thenReturn(config(2L, "WECOM_APP", "{}"));

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_SUCCESS));

        verify(emailChannel, times(1)).send(any(NotificationContext.class));
        verify(wecomAppChannel, times(1)).send(any(NotificationContext.class));
    }

    @Test
    void failedChannelShouldRetryAndThenSucceed() throws Exception {
        NotificationRule rule = new NotificationRule();
        rule.setId(7L);
        rule.setEventType("TASK_COMPLETED");
        rule.setChannel("EMAIL");
        rule.setConfigId(1L);
        rule.setEnabled(1);

        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_COMPLETED", "TEST_TASK"))
                .thenReturn(List.of(rule));
        when(notificationConfigService.getById(1L))
                .thenReturn(config(1L, "EMAIL", "{}"));
        when(emailChannel.resolveRecipient(any(NotificationContext.class)))
                .thenReturn("1");
        doThrow(new RuntimeException("网络异常"))
                .doNothing()
                .when(emailChannel).send(any(NotificationContext.class));

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_COMPLETED));

        verify(emailChannel, times(2)).send(any(NotificationContext.class));
        verify(notificationLogService, times(2)).save(any());
    }

    @Test
    void aiOptimizeFlagShouldWrapChannelAndOptimizeContent() throws Exception {
        NotificationRule rule = new NotificationRule();
        rule.setId(8L);
        rule.setEventType("TASK_COMPLETED");
        rule.setChannel("EMAIL");
        rule.setConfigId(1L);
        rule.setSubject("原始标题");
        rule.setBody("原始正文");
        rule.setAiOptimizeNotify(1);
        rule.setEnabled(1);

        when(notificationRuleService.findEnabledByEventTypeAndTask("TASK_COMPLETED", "TEST_TASK"))
                .thenReturn(List.of(rule));
        when(notificationConfigService.getById(1L))
                .thenReturn(config(1L, "EMAIL", "{}"));
        when(emailChannel.resolveRecipient(any(NotificationContext.class)))
                .thenReturn("1");
        when(aiAssistantService.optimizeNotification(anyString(), anyString(), anyString(), isNull()))
                .thenReturn(new AiAssistantService.NotificationContent("优化标题", "优化正文"));

        listener.onTaskExecutionEvent(event(TaskExecutionEvent.EventType.TASK_COMPLETED));

        ArgumentCaptor<NotificationContext> captor = ArgumentCaptor.forClass(NotificationContext.class);
        verify(emailChannel, times(1)).send(captor.capture());
        NotificationContext ctx = captor.getValue();
        assertEquals("优化标题", ctx.getSubject());
        assertEquals("优化正文", ctx.getBody());
    }
}
