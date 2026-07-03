package com.mattoid.scheduled.template;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractPoiTemplateProcessor implements TemplateProcessor {

    protected static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    protected String replacePlaceholders(String text, Map<String, Object> data) {
        return replacePlaceholders(text, data, true);
    }

    protected String replacePlaceholders(String text, Map<String, Object> data, boolean clean) {
        if (text == null) {
            return text;
        }
        if (data == null) {
            data = Collections.emptyMap();
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = data.get(key);
            if (value == null && !clean) {
                // 链式处理中保留未匹配占位符，留给下一条 SQL 处理
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(sb, value == null ? "" : Matcher.quoteReplacement(value.toString()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    protected void replacePlaceholdersInSheet(Sheet sheet, Map<String, Object> data) {
        replacePlaceholdersInSheet(sheet, data, true);
    }

    protected void replacePlaceholdersInSheet(Sheet sheet, Map<String, Object> data, boolean clean) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String val = getCellStringValue(cell);
                if (val != null && val.contains("${")) {
                    setCellValue(cell, replacePlaceholders(val, data, clean));
                }
            }
        }
    }

    protected void replacePlaceholdersInWord(XWPFDocument document, Map<String, Object> data) {
        replacePlaceholdersInWord(document, data, true);
    }

    protected void replacePlaceholdersInWord(XWPFDocument document, Map<String, Object> data, boolean clean) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            replaceParagraphRuns(paragraph, data, clean);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        replaceParagraphRuns(paragraph, data, clean);
                    }
                }
            }
        }
    }

    protected void replaceParagraphRuns(XWPFParagraph paragraph, Map<String, Object> data) {
        replaceParagraphRuns(paragraph, data, true);
    }

    protected void replaceParagraphRuns(XWPFParagraph paragraph, Map<String, Object> data, boolean clean) {
        String fullText = paragraph.getText();
        if (fullText == null || !fullText.contains("${")) return;

        String replaced = replacePlaceholders(fullText, data, clean);
        // 简化处理：清空所有 run 后写入第一个 run
        for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
        XWPFRun run = paragraph.createRun();
        run.setText(replaced);
    }

    protected void replacePlaceholdersInPpt(XMLSlideShow ppt, Map<String, Object> data) {
        replacePlaceholdersInPpt(ppt, data, true);
    }

    protected void replacePlaceholdersInPpt(XMLSlideShow ppt, Map<String, Object> data, boolean clean) {
        for (XSLFSlide slide : ppt.getSlides()) {
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape textShape) {
                    List<XSLFTextParagraph> paragraphs = new ArrayList<>(textShape.getTextParagraphs());
                    for (XSLFTextParagraph paragraph : paragraphs) {
                        List<XSLFTextRun> runs = new ArrayList<>(paragraph.getTextRuns());
                        for (XSLFTextRun run : runs) {
                            String text = run.getRawText();
                            if (text != null && text.contains("${")) {
                                run.setText(replacePlaceholders(text, data, clean));
                            }
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
