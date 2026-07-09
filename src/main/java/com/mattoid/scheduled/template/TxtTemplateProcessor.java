package com.mattoid.scheduled.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TxtTemplateProcessor implements TemplateProcessor {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    @Override
    public boolean supports(String templateType) {
        return "TXT".equalsIgnoreCase(templateType);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName) throws Exception {
        return process(templateFile, data, outputFileName, true);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName, boolean cleanPlaceholders) throws Exception {
        String content = Files.readString(templateFile.toPath(), StandardCharsets.UTF_8);

        StringBuilder dataRows = new StringBuilder();
        for (Map<String, Object> row : data) {
            dataRows.append(replacePlaceholders(content, row, cleanPlaceholders)).append(System.lineSeparator());
        }

        if (data.isEmpty()) {
            dataRows.append(replacePlaceholders(content, Collections.emptyMap(), cleanPlaceholders));
        }

        File output = new File(outputFileName);
        log.debug("TXT 文件预写入内容预览: path={}, contentPreview={}",
                outputFileName,
                abbreviate(dataRows.toString(), 800));
        Files.writeString(output.toPath(), dataRows.toString(), StandardCharsets.UTF_8);
        return output;
    }

    private String replacePlaceholders(String text, Map<String, Object> data, boolean clean) {
        if (text == null) return "";
        if (data == null) data = Collections.emptyMap();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = data.get(key);
            if (value == null && !clean) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(sb, value == null ? "" : Matcher.quoteReplacement(value.toString()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(" + (text.length() - maxLength) + " more chars)";
    }
}
