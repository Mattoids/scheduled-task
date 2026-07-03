package com.mattoid.scheduled.template;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class WordTemplateProcessor extends AbstractPoiTemplateProcessor {

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
        try (FileInputStream fis = new FileInputStream(templateFile);
             XWPFDocument document = new XWPFDocument(fis)) {

            Map<String, Object> placeholderData = data.isEmpty() ? Collections.emptyMap() : data.get(0);
            replacePlaceholdersInWord(document, placeholderData, cleanPlaceholders);

            File output = new File(outputFileName);
            try (FileOutputStream fos = new FileOutputStream(output)) {
                document.write(fos);
            }
            return output;
        }
    }
}
