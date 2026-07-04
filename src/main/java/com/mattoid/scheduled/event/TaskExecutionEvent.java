package com.mattoid.scheduled.event;

import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.File;
import java.util.Collections;
import java.util.List;

@Getter
public class TaskExecutionEvent extends ApplicationEvent {

    private final TaskConfig task;
    private final TaskLog taskLog;
    private final List<File> reportFiles;
    private final List<InlineSqlResult> inlineResults;
    private final EventType eventType;

    public TaskExecutionEvent(Object source,
                              TaskConfig task,
                              TaskLog taskLog,
                              List<File> reportFiles,
                              EventType eventType) {
        this(source, task, taskLog, reportFiles, Collections.emptyList(), eventType);
    }

    public TaskExecutionEvent(Object source,
                              TaskConfig task,
                              TaskLog taskLog,
                              List<File> reportFiles,
                              List<InlineSqlResult> inlineResults,
                              EventType eventType) {
        super(source);
        this.task = task;
        this.taskLog = taskLog;
        this.reportFiles = reportFiles;
        this.inlineResults = inlineResults;
        this.eventType = eventType;
    }

    public enum EventType {
        TASK_SUCCESS,
        TASK_FAILURE,
        TASK_COMPLETED
    }
}
