package com.mattoid.scheduled.template;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CsvTemplateProcessor implements TemplateProcessor {

    @Override
    public boolean supports(String templateType) {
        return "CSV".equalsIgnoreCase(templateType);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName) throws Exception {
        List<String> headers = new ArrayList<>();
        try (Reader reader = new FileReader(templateFile, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build())) {
            headers.addAll(parser.getHeaderNames());
        } catch (Exception e) {
            // 如果模板没有合法表头，则使用数据 key 作为表头
            if (!data.isEmpty()) {
                headers.addAll(data.get(0).keySet());
            }
        }

        File output = new File(outputFileName);
        try (Writer writer = new FileWriter(output, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader(headers.toArray(new String[0]))
                     .build())) {
            for (Map<String, Object> row : data) {
                List<Object> values = new ArrayList<>();
                for (String h : headers) {
                    values.add(row.getOrDefault(h, ""));
                }
                printer.printRecord(values);
            }
        }
        return output;
    }
}
