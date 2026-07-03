package com.mattoid.scheduled.template;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TxtTemplateProcessor implements TemplateProcessor {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    @Override
    public boolean supports(String templateType) {
        return "TXT".equalsIgnoreCase(templateType);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName) throws Exception {
        String content = Files.readString(templateFile.toPath(), StandardCharsets.UTF_8);

        StringBuilder dataRows = new StringBuilder();
        for (Map<String, Object> row : data) {
            dataRows.append(replacePlaceholders(content, row)).append(System.lineSeparator());
        }

        if (data.isEmpty()) {
            dataRows.append(replacePlaceholders(content, null));
        }

        File output = new File(outputFileName);
        Files.writeString(output.toPath(), dataRows.toString(), StandardCharsets.UTF_8);
        return output;
    }

    private String replacePlaceholders(String text, Map<String, Object> data) {
        if (text == null) return "";
        if (data == null) return text;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = data.getOrDefault(key, "");
            matcher.appendReplacement(sb, value == null ? "" : Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
