package com.mattoid.scheduled.template;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class WordTemplateProcessor extends AbstractPoiTemplateProcessor {

    private static final Logger log = LoggerFactory.getLogger(WordTemplateProcessor.class);

    @Override
    public boolean supports(String templateType) {
        return "WORD".equalsIgnoreCase(templateType);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName) throws Exception {
        return process(templateFile, data, outputFileName, true);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName, boolean cleanPlaceholders) throws Exception {
        long start = System.currentTimeMillis();
        log.info("开始处理 Word 模板: {}, 数据行数: {}", templateFile.getName(), data.size());
        try (FileInputStream fis = new FileInputStream(templateFile);
             XWPFDocument document = new XWPFDocument(fis)) {

            if (!data.isEmpty()) {
                expandTablesInWord(document, data);
            }
            Map<String, Object> placeholderData = data.isEmpty() ? Collections.emptyMap() : data.get(0);
            replacePlaceholdersInWord(document, placeholderData, cleanPlaceholders);

            File output = new File(outputFileName);
            try (FileOutputStream fos = new FileOutputStream(output)) {
                document.write(fos);
            }
            log.info("Word 模板处理完成: {}, 耗时: {}ms, 输出: {}", templateFile.getName(), System.currentTimeMillis() - start, outputFileName);
            return output;
        }
    }
}
