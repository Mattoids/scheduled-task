package com.mattoid.scheduled.template;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
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
public class PptTemplateProcessor extends AbstractPoiTemplateProcessor {

    private static final Logger log = LoggerFactory.getLogger(PptTemplateProcessor.class);

    @Override
    public boolean supports(String templateType) {
        return "PPT".equalsIgnoreCase(templateType);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName) throws Exception {
        return process(templateFile, data, outputFileName, true);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName, boolean cleanPlaceholders) throws Exception {
        long start = System.currentTimeMillis();
        log.info("开始处理 PPT 模板: {}, 数据行数: {}", templateFile.getName(), data.size());
        try (FileInputStream fis = new FileInputStream(templateFile);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {

            if (!data.isEmpty()) {
                expandTablesInPpt(ppt, data);
            }
            Map<String, Object> placeholderData = data.isEmpty() ? Collections.emptyMap() : data.get(0);
            replacePlaceholdersInPpt(ppt, placeholderData, cleanPlaceholders);

            File output = new File(outputFileName);
            try (FileOutputStream fos = new FileOutputStream(output)) {
                ppt.write(fos);
            }
            log.info("PPT 模板处理完成: {}, 耗时: {}ms, 输出: {}", templateFile.getName(), System.currentTimeMillis() - start, outputFileName);
            return output;
        }
    }
}
