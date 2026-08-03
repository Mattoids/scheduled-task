package com.mattoid.scheduled.template;

import com.mattoid.scheduled.service.ChartGenerationService;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class PptTemplateProcessor extends AbstractPoiTemplateProcessor {

    private static final Logger log = LoggerFactory.getLogger(PptTemplateProcessor.class);

    private final ChartGenerationService chartGenerationService;

    public PptTemplateProcessor(ChartGenerationService chartGenerationService) {
        this.chartGenerationService = chartGenerationService;
    }

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
        return processWithContext(templateFile, data, outputFileName, cleanPlaceholders, null);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName,
                        boolean cleanPlaceholders, Map<String, Object> context) throws Exception {
        return processWithContext(templateFile, data, outputFileName, cleanPlaceholders, context);
    }

    private File processWithContext(File templateFile, List<Map<String, Object>> data, String outputFileName,
                                    boolean cleanPlaceholders, Map<String, Object> context) throws Exception {
        long start = System.currentTimeMillis();
        log.info("开始处理 PPT 模板: {}, 数据行数: {}", templateFile.getName(), data.size());
        try (FileInputStream fis = new FileInputStream(templateFile);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {

            expandTablesInPpt(ppt, data);

            if (context != null && !data.isEmpty()) {
                insertCharts(ppt, data, context);
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

    private void insertCharts(XMLSlideShow ppt, List<Map<String, Object>> data, Map<String, Object> context) {
        Object enabledObj = context.get("chartEnabled");
        if (!(enabledObj instanceof Integer enabled) || enabled != 1) {
            log.info("SQL 未启用图表生成，跳过 PPT 图表插入: sqlCode={}", context.get("sqlCode"));
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
            log.warn("PPT 图表生成失败，跳过插入: sqlCode={}", sqlCode);
            return;
        }

        boolean inserted = false;
        for (XSLFSlide slide : ppt.getSlides()) {
            List<XSLFShape> shapes = new ArrayList<>(slide.getShapes());
            for (XSLFShape shape : shapes) {
                if (shape instanceof XSLFTextShape textShape) {
                    if (containsChartPlaceholder(textShape.getText(), sqlCode)) {
                        replaceShapeWithPicture(slide, shape, chartFile);
                        inserted = true;
                    }
                } else if (shape instanceof XSLFTable table) {
                    Rectangle2D tableAnchor = table.getAnchor();
                    for (XSLFTableRow row : table.getRows()) {
                        for (XSLFTableCell cell : row.getCells()) {
                            if (containsChartPlaceholder(cell.getText(), sqlCode)) {
                                clearCellText(cell);
                                Rectangle2D cellAnchor = cell.getAnchor();
                                Rectangle2D absoluteAnchor = new Rectangle2D.Double(
                                        tableAnchor.getX() + cellAnchor.getX(),
                                        tableAnchor.getY() + cellAnchor.getY(),
                                        cellAnchor.getWidth(),
                                        cellAnchor.getHeight());
                                addPictureToSlide(slide, absoluteAnchor, chartFile);
                                inserted = true;
                            }
                        }
                    }
                }
            }
        }
        if (!inserted) {
            log.warn("PPT 中未找到匹配的图表占位符: sqlCode={}, placeholder=${chart:{}} 或 ${chart}", sqlCode, sqlCode);
        }
    }

    private void clearCellText(XSLFTableCell cell) {
        for (XSLFTextParagraph paragraph : new ArrayList<>(cell.getTextParagraphs())) {
            for (XSLFTextRun run : new ArrayList<>(paragraph.getTextRuns())) {
                paragraph.removeTextRun(run);
            }
        }
    }

    private void replaceShapeWithPicture(XSLFSlide slide, XSLFShape placeholder, File imageFile) {
        try {
            addPictureToSlide(slide, placeholder.getAnchor(), imageFile);
            slide.removeShape(placeholder);
            log.debug("PPT 替换图表占位符为图片: {}", imageFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("PPT 替换图表占位符失败", e);
        }
    }

    private void addPictureToSlide(XSLFSlide slide, Rectangle2D anchor, File imageFile) {
        try {
            XMLSlideShow ppt = slide.getSlideShow();
            XSLFPictureData pictureData = ppt.addPicture(imageFile, PictureData.PictureType.PNG);
            XSLFPictureShape picture = slide.createPicture(pictureData);
            picture.setAnchor(anchor);
            log.debug("PPT 插入图表: {}", imageFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("PPT 插入图表失败", e);
        }
    }
}
