package com.mattoid.scheduled.template;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface TemplateProcessor {

    boolean supports(String templateType);

    File process(File templateFile, List<Map<String, Object>> data, String outputFileName) throws Exception;
}
