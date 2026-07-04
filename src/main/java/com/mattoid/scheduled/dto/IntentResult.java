package com.mattoid.scheduled.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class IntentResult {

    /**
     * 识别出的意图，如 VIEW_TASKS / TRIGGER_TASK / VIEW_LOGS / UNKNOWN
     */
    private String action;

    /**
     * 意图参数
     */
    private Map<String, String> params = new HashMap<>();

    /**
     * AI 对原始内容的理解摘要
     */
    private String summary;

    /**
     * 是否成功识别
     */
    private boolean recognized;
}
