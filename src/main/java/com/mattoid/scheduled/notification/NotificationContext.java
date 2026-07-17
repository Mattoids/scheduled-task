package com.mattoid.scheduled.notification;

import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.NotificationRule;
import com.mattoid.scheduled.event.TaskExecutionEvent;

/**
 * 通知渠道统一上下文入参。
 */
public class NotificationContext {

    private final TaskExecutionEvent event;
    private final NotificationRule rule;
    private final NotificationConfig config;
    private String subject;
    private String body;

    public NotificationContext(TaskExecutionEvent event, NotificationRule rule, NotificationConfig config) {
        this.event = event;
        this.rule = rule;
        this.config = config;
    }

    public TaskExecutionEvent getEvent() {
        return event;
    }

    public NotificationRule getRule() {
        return rule;
    }

    public NotificationConfig getConfig() {
        return config;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
