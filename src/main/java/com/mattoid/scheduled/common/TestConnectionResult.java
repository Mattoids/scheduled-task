package com.mattoid.scheduled.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestConnectionResult {

    private boolean success;
    private String message;
    private String stage;
    private List<StageResult> stages;

    public TestConnectionResult(boolean success, String message, String stage) {
        this.success = success;
        this.message = message;
        this.stage = stage;
    }

    public static TestConnectionResult ok() {
        return new TestConnectionResult(true, null, null, null);
    }

    public static TestConnectionResult ok(List<StageResult> stages) {
        return new TestConnectionResult(true, null, null, stages);
    }

    public static TestConnectionResult fail(String stage, String message) {
        return new TestConnectionResult(false, message, stage, null);
    }

    public static TestConnectionResult fail(String stage, String message, List<StageResult> stages) {
        return new TestConnectionResult(false, message, stage, stages);
    }

    public static TestConnectionResult fail(String message) {
        return new TestConnectionResult(false, message, null, null);
    }

    public void addStage(String stage, boolean success, String message) {
        if (stages == null) {
            stages = new ArrayList<>();
        }
        stages.add(new StageResult(stage, success, message));
    }
}
