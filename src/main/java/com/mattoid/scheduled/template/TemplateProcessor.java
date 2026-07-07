package com.mattoid.scheduled.template;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface TemplateProcessor {

    boolean supports(String templateType);

    File process(File templateFile, List<Map<String, Object>> data, String outputFileName) throws Exception;

    default File process(File templateFile, List<Map<String, Object>> data, String outputFileName, boolean cleanPlaceholders) throws Exception {
        return process(templateFile, data, outputFileName);
    }

    /**
     * 带上下文的模板处理，默认降级到 4 参数方法。
     * context 可携带 SQL 元数据（如 chartEnabled、chartType、sheetName 等），
     * 由具体处理器选择消费。
     */
    default File process(File templateFile, List<Map<String, Object>> data, String outputFileName, boolean cleanPlaceholders, Map<String, Object> context) throws Exception {
        return process(templateFile, data, outputFileName, cleanPlaceholders);
    }
}
