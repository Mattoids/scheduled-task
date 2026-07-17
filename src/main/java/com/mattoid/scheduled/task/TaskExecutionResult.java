package com.mattoid.scheduled.task;

import com.mattoid.scheduled.event.InlineResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务执行结果聚合对象，由处理器生成、编排器消费。
 */
public class TaskExecutionResult {

    private final List<File> reportFiles = new ArrayList<>();
    private final List<File> notifyFiles = new ArrayList<>();
    private final List<InlineResult> inlineResults = new ArrayList<>();
    private final Map<String, File> chartFiles = new LinkedHashMap<>();

    public void addFile(File file) {
        addFile(file, file);
    }

    public void addFile(File outputFile, File notifyFile) {
        if (outputFile != null) {
            reportFiles.add(outputFile);
        }
        if (notifyFile != null) {
            notifyFiles.add(notifyFile);
        }
    }

    public void addInline(InlineResult inlineResult) {
        if (inlineResult != null) {
            inlineResults.add(inlineResult);
        }
    }

    public void addChartFile(String code, File chartFile) {
        if (chartFile != null) {
            chartFiles.put(code, chartFile);
        }
    }

    public List<File> getReportFiles() {
        return Collections.unmodifiableList(reportFiles);
    }

    public List<File> getNotifyFiles() {
        return Collections.unmodifiableList(notifyFiles);
    }

    public List<InlineResult> getInlineResults() {
        return Collections.unmodifiableList(inlineResults);
    }

    public Map<String, File> getChartFiles() {
        return Collections.unmodifiableMap(chartFiles);
    }
}
