package com.mattoid.scheduled.template;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
        try (FileInputStream fis = new FileInputStream(templateFile);
             XWPFDocument document = new XWPFDocument(fis)) {

            replacePlaceholdersInWord(document, data.isEmpty() ? null : data.get(0));

            File output = new File(outputFileName);
            try (FileOutputStream fos = new FileOutputStream(output)) {
                document.write(fos);
            }
            return output;
        }
    }
}
