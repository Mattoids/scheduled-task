package com.mattoid.scheduled.event;

import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
public class TaskExecutionEvent extends ApplicationEvent {

    private final TaskConfig task;
    private final TaskLog taskLog;
    private final List<File> reportFiles;
    private final List<File> notifyFiles;
    private final List<? extends InlineResult> inlineResults;
    private final Map<String, File> chartFiles;
    private final EventType eventType;

    public TaskExecutionEvent(Object source,
                              TaskConfig task,
                              TaskLog taskLog,
                              List<File> reportFiles,
                              EventType eventType) {
        this(source, task, taskLog, reportFiles, reportFiles, Collections.emptyList(), Collections.emptyMap(), eventType);
    }

    public TaskExecutionEvent(Object source,
                              TaskConfig task,
                              TaskLog taskLog,
                              List<File> reportFiles,
                              List<? extends InlineResult> inlineResults,
                              EventType eventType) {
        this(source, task, taskLog, reportFiles, reportFiles, inlineResults, Collections.emptyMap(), eventType);
    }

    public TaskExecutionEvent(Object source,
                              TaskConfig task,
                              TaskLog taskLog,
                              List<File> reportFiles,
                              List<? extends InlineResult> inlineResults,
                              Map<String, File> chartFiles,
                              EventType eventType) {
        this(source, task, taskLog, reportFiles, reportFiles, inlineResults, chartFiles, eventType);
    }

    public TaskExecutionEvent(Object source,
                              TaskConfig task,
                              TaskLog taskLog,
                              List<File> reportFiles,
                              List<File> notifyFiles,
                              List<? extends InlineResult> inlineResults,
                              Map<String, File> chartFiles,
                              EventType eventType) {
        super(source);
        this.task = task;
        this.taskLog = taskLog;
        this.reportFiles = reportFiles;
        this.notifyFiles = notifyFiles;
        this.inlineResults = inlineResults;
        this.chartFiles = chartFiles;
        this.eventType = eventType;
    }

    public enum EventType {
        TASK_SUCCESS,
        TASK_FAILURE,
        TASK_COMPLETED
    }
}
