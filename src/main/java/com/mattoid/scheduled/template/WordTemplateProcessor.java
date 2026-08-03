package com.mattoid.scheduled.template;

import com.mattoid.scheduled.service.ChartGenerationService;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class WordTemplateProcessor extends AbstractPoiTemplateProcessor {

    private static final Logger log = LoggerFactory.getLogger(WordTemplateProcessor.class);

    private final ChartGenerationService chartGenerationService;

    public WordTemplateProcessor(ChartGenerationService chartGenerationService) {
        this.chartGenerationService = chartGenerationService;
    }

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
        return process(templateFile, data, outputFileName, cleanPlaceholders, null);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName,
                        boolean cleanPlaceholders, Map<String, Object> context) throws Exception {
        long start = System.currentTimeMillis();
        log.info("开始处理 Word 模板: {}, 数据行数: {}", templateFile.getName(), data.size());
        try (FileInputStream fis = new FileInputStream(templateFile);
             XWPFDocument document = new XWPFDocument(fis)) {

            if (!data.isEmpty()) {
                expandTablesInWord(document, data);
            }

            if (context != null && !data.isEmpty()) {
                insertCharts(document, data, context);
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

    private void insertCharts(XWPFDocument document, List<Map<String, Object>> data, Map<String, Object> context) {
        Object enabledObj = context.get("chartEnabled");
        if (!(enabledObj instanceof Integer enabled) || enabled != 1) {
            log.info("SQL 未启用图表生成，跳过 Word 图表插入: sqlCode={}", context.get("sqlCode"));
            return;
        }
        String sqlCode = context.get("sqlCode") == null ? null : context.get("sqlCode").toString();
        String chartType = context.get("chartType") == null ? null : context.get("chartType").toString();
        String chartTitle = context.get("chartTitle") == null ? null : context.get("chartTitle").toString();
        String chartBackgroundColor = context.get("chartBackgroundColor") == null ? null : context.get("chartBackgroundColor").toString();
        String chartFontFamily = context.get("chartFontFamily") == null ? null : context.get("chartFontFamily").toString();
        Integer chartFontSize = context.get("chartFontSize") == null ? null : (Integer) context.get("chartFontSize");
        if (!StringUtils.hasText(chartTitle)) {
            Object sqlName = context.get("sqlName");
            chartTitle = sqlName == null ? "数据图表" : sqlName.toString();
        }

        File chartFile = (File) context.get("chartFile");
        if (chartFile == null || !chartFile.exists()) {
            chartFile = chartGenerationService.generateChart(data, chartType, chartTitle, true, "AUTO",
                    chartBackgroundColor, chartFontFamily, chartFontSize);
        }
        if (chartFile == null || !chartFile.exists()) {
            log.warn("Word 图表生成失败，跳过插入: sqlCode={}", sqlCode);
            return;
        }

        boolean inserted = false;
        for (XWPFParagraph paragraph : new ArrayList<>(document.getParagraphs())) {
            if (containsChartPlaceholder(paragraph.getText(), sqlCode)) {
                replaceParagraphWithPicture(paragraph, chartFile);
                inserted = true;
            }
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : new ArrayList<>(cell.getParagraphs())) {
                        if (containsChartPlaceholder(paragraph.getText(), sqlCode)) {
                            replaceParagraphWithPicture(paragraph, chartFile);
                            inserted = true;
                        }
                    }
                }
            }
        }
        if (!inserted) {
            log.warn("Word 中未找到匹配的图表占位符: sqlCode={}", sqlCode);
        }
    }

    private void replaceParagraphWithPicture(XWPFParagraph paragraph, File imageFile) {
        try {
            for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }
            XWPFRun run = paragraph.createRun();
            try (FileInputStream imageStream = new FileInputStream(imageFile)) {
                run.addPicture(imageStream, XWPFDocument.PICTURE_TYPE_PNG, imageFile.getName(),
                        Units.toEMU(450), Units.toEMU(270));
            }
            log.debug("Word 插入图表: {}", imageFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Word 插入图表失败", e);
        }
    }
}
