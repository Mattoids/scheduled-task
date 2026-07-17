package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.event.TaskExecutionEvent;
import com.mattoid.scheduled.task.TaskExecutionResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 通知分发器：根据任务执行结果发布对应事件。
 */
@Component
public class NotificationDispatcher {

    private final ApplicationEventPublisher eventPublisher;

    public NotificationDispatcher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void dispatch(TaskConfig task, TaskLog taskLog, TaskExecutionResult result) {
        String status = taskLog.getStatus();
        if ("SUCCESS".equals(status)) {
            eventPublisher.publishEvent(new TaskExecutionEvent(this, task, taskLog,
                    result.getReportFiles(), result.getNotifyFiles(), result.getInlineResults(),
                    result.getChartFiles(), TaskExecutionEvent.EventType.TASK_SUCCESS));
            eventPublisher.publishEvent(new TaskExecutionEvent(this, task, taskLog,
                    result.getReportFiles(), result.getNotifyFiles(), result.getInlineResults(),
                    result.getChartFiles(), TaskExecutionEvent.EventType.TASK_COMPLETED));
        } else if ("FAILED".equals(status)) {
            eventPublisher.publishEvent(new TaskExecutionEvent(this, task, taskLog,
                    result.getReportFiles(), result.getNotifyFiles(), result.getInlineResults(),
                    result.getChartFiles(), TaskExecutionEvent.EventType.TASK_FAILURE));
        }
    }
}
