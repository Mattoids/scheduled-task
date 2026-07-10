package com.mattoid.scheduled.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class SqlGenerateResult {

    private String sql;
    private Map<String, String> params = new HashMap<>();
    private String explanation;

    public static SqlGenerateResult fail(String message) {
        SqlGenerateResult result = new SqlGenerateResult();
        result.setSql("");
        result.setExplanation(message);
        return result;
    }
}
