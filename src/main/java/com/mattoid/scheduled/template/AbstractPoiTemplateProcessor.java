package com.mattoid.scheduled.template;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractPoiTemplateProcessor implements TemplateProcessor {

    protected static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    protected String replacePlaceholders(String text, Map<String, Object> data) {
        if (text == null || data == null) {
            return text;
        }
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

    protected void replacePlaceholdersInSheet(Sheet sheet, Map<String, Object> data) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String val = getCellStringValue(cell);
                if (val != null && val.contains("${")) {
                    setCellValue(cell, replacePlaceholders(val, data));
                }
            }
        }
    }

    protected void replacePlaceholdersInWord(XWPFDocument document, Map<String, Object> data) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            replaceParagraphRuns(paragraph, data);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        replaceParagraphRuns(paragraph, data);
                    }
                }
            }
        }
    }

    protected void replaceParagraphRuns(XWPFParagraph paragraph, Map<String, Object> data) {
        String fullText = paragraph.getText();
        if (fullText == null || !fullText.contains("${")) return;

        String replaced = replacePlaceholders(fullText, data);
        // 简化处理：清空所有 run 后写入第一个 run
        for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
        XWPFRun run = paragraph.createRun();
        run.setText(replaced);
    }

    protected void replacePlaceholdersInPpt(XMLSlideShow ppt, Map<String, Object> data) {
        for (XSLFSlide slide : ppt.getSlides()) {
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape textShape) {
                    for (XSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
                        String text = paragraph.getText();
                        if (text != null && text.contains("${")) {
                            textShape.setText(replacePlaceholders(text, data));
                        }
                    }
                }
            }
        }
    }

    protected String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    protected void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(value.toString());
        }
    }
}
