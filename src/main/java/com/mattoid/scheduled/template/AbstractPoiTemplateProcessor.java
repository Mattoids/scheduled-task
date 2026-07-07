package com.mattoid.scheduled.template;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTable;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractPoiTemplateProcessor implements TemplateProcessor {

    private static final Logger log = LoggerFactory.getLogger(AbstractPoiTemplateProcessor.class);

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
        int slideIndex = 0;
        for (XSLFSlide slide : ppt.getSlides()) {
            log.debug("Replacing placeholders in PPT slide {}", slideIndex);
            for (XSLFShape shape : slide.getShapes()) {
                if (shape instanceof XSLFTextShape textShape) {
                    replacePlaceholdersInPptTextShape(textShape, data, clean);
                } else if (shape instanceof XSLFTable table) {
                    for (XSLFTableRow row : table.getRows()) {
                        for (XSLFTableCell cell : row.getCells()) {
                            replacePlaceholdersInPptTextShape(cell, data, clean);
                        }
                    }
                }
            }
            slideIndex++;
        }
    }

    protected boolean isSequenceHeader(String header) {
        if (header == null) {
            return false;
        }
        String trimmed = header.trim();
        return "序号".equals(trimmed) || "seq".equalsIgnoreCase(trimmed);
    }

    /**
     * 从单元格文本中提取字段名：支持 ${key} 和纯文本 key，空单元格返回 null。
     */
    protected String extractHeaderKey(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (isSequenceHeader(trimmed)) {
            return "序号";
        }
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return trimmed.substring(2, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 判断表格中的一行是否为数据表头：
     * 1. 至少有一个单元格是 ${key} 且 key 存在于数据中；
     * 2. 或者所有非空单元格的纯文本都对应数据字段。
     * 返回表头字段数组；如果不是数据表头则返回 null。
     */
    protected String[] resolveDataHeaders(List<String> cellTexts, Set<String> dataKeys) {
        // 优先匹配包含 ${key} 的行
        boolean hasMatchingPlaceholder = false;
        for (String text : cellTexts) {
            String trimmed = text == null ? "" : text.trim();
            if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
                String key = trimmed.substring(2, trimmed.length() - 1);
                if (dataKeys.contains(key) || isSequenceHeader(key)) {
                    hasMatchingPlaceholder = true;
                    break;
                }
            }
        }
        if (hasMatchingPlaceholder) {
            String[] headers = new String[cellTexts.size()];
            for (int c = 0; c < cellTexts.size(); c++) {
                headers[c] = extractHeaderKey(cellTexts.get(c));
            }
            return headers;
        }

        // 回退：纯文本表头，要求所有非空单元格都对应数据字段
        boolean hasKey = false;
        String[] headers = new String[cellTexts.size()];
        for (int c = 0; c < cellTexts.size(); c++) {
            String key = extractHeaderKey(cellTexts.get(c));
            if (key == null) {
                headers[c] = null;
                continue;
            }
            if (dataKeys.contains(key) || isSequenceHeader(key)) {
                headers[c] = key;
                hasKey = true;
            } else {
                return null;
            }
        }
        return hasKey ? headers : null;
    }

    protected void expandTablesInWord(XWPFDocument document, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Set<String> dataKeys = data.get(0).keySet();
        for (XWPFTable table : document.getTables()) {
            int headerRowIndex = -1;
            String[] headers = null;
            for (int r = 0; r < table.getNumberOfRows(); r++) {
                XWPFTableRow row = table.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> texts = new ArrayList<>();
                for (XWPFTableCell cell : row.getTableCells()) {
                    texts.add(cell.getText());
                }
                String[] candidate = resolveDataHeaders(texts, dataKeys);
                if (candidate != null) {
                    headerRowIndex = r;
                    headers = candidate;
                    break;
                }
            }
            if (headerRowIndex < 0 || headers == null) {
                continue;
            }

            XWPFTableRow headerRow = table.getRow(headerRowIndex);
            // 只有表头没有样例行时，若只有一行数据则按普通占位符处理，避免误把数据行当表头
            boolean hasSampleRow = headerRowIndex + 1 < table.getNumberOfRows();
            if (!hasSampleRow && data.size() <= 1) {
                continue;
            }

            log.debug("Expanding Word table: headerRow={}, headers={}", headerRowIndex, Arrays.toString(headers));

            // 清理表头显示：去掉 ${}
            for (int c = 0; c < headerRow.getTableCells().size() && c < headers.length; c++) {
                if (headers[c] != null) {
                    headerRow.getCell(c).setText(headers[c]);
                }
            }

            // 记录样例行，后续新增行克隆其格式（删除前获取并复制 XML）
            CTRow sampleRowTemplate = null;
            if (hasSampleRow) {
                sampleRowTemplate = (CTRow) table.getRow(headerRowIndex + 1).getCtRow().copy();
            }

            // 删除表头下方的原有行（兼容只有表头没有样例行的表格）
            for (int i = table.getRows().size() - 1; i > headerRowIndex; i--) {
                table.removeRow(i);
            }

            // 填充数据行
            for (int i = 0; i < data.size(); i++) {
                Map<String, Object> rowData = data.get(i);
                XWPFTableRow newRow;
                if (sampleRowTemplate != null) {
                    CTRow clonedRow = (CTRow) sampleRowTemplate.copy();
                    XWPFTableRow tempRow = new XWPFTableRow(clonedRow, table);
                    table.addRow(tempRow, headerRowIndex + 1 + i);
                    // addRow 会复制 row，必须取实际插入到 XML 中的行才能修改生效
                    newRow = new XWPFTableRow(table.getCTTbl().getTrArray(headerRowIndex + 1 + i), table);
                } else {
                    newRow = table.createRow();
                }
                for (int c = 0; c < newRow.getTableCells().size() && c < headers.length; c++) {
                    String header = headers[c];
                    Object value = isSequenceHeader(header) ? (i + 1) : rowData.get(header);
                    setWordCellText(newRow.getCell(c), value == null ? "" : value.toString());
                }
            }
        }
    }

    protected void expandTablesInPpt(XMLSlideShow ppt, List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        // 单行数据按普通占位符处理，多行数据才展开表格
        if (data.size() <= 1) {
            return;
        }
        Set<String> dataKeys = data.get(0).keySet();
        int slideIndex = 0;
        for (XSLFSlide slide : ppt.getSlides()) {
            List<XSLFShape> shapes = slide.getShapes();
            for (int shapeIndex = 0; shapeIndex < shapes.size(); shapeIndex++) {
                XSLFShape shape = shapes.get(shapeIndex);
                if (!(shape instanceof XSLFTable table)) {
                    continue;
                }
                log.debug("Processing PPT table on slide {}, shape {}", slideIndex, shapeIndex);
                List<XSLFTableRow> rows = table.getRows();
                int headerRowIndex = -1;
                String[] headers = null;
                for (int r = 0; r < rows.size(); r++) {
                    XSLFTableRow row = rows.get(r);
                    if (row == null) {
                        continue;
                    }
                    List<String> texts = new ArrayList<>();
                    for (XSLFTableCell cell : row.getCells()) {
                        texts.add(getTextShapeText(cell));
                    }
                    String[] candidate = resolveDataHeaders(texts, dataKeys);
                    if (candidate != null) {
                        headerRowIndex = r;
                        headers = candidate;
                        break;
                    }
                }
                if (headerRowIndex < 0 || headers == null) {
                    continue;
                }
                log.debug("Expanding PPT table on slide {}, shape {}: headerRow={}, headers={}, dataRows={}", slideIndex, shapeIndex, headerRowIndex, Arrays.toString(headers), data.size());

                XSLFTableRow headerRow = rows.get(headerRowIndex);
                // 支持只有表头没有样例行的表格：没有样例行时直接新增数据行
                int existingDataRows = rows.size() - headerRowIndex - 1;
                // 提前复制样例行 XML，避免后续修改后无法克隆格式
                CTTableRow sampleRowTemplate = existingDataRows > 0
                        ? (CTTableRow) rows.get(headerRowIndex + 1).getXmlObject().copy()
                        : null;
                double templateRowHeight = sampleRowTemplate != null
                        ? rows.get(headerRowIndex + 1).getHeight()
                        : headerRow.getHeight();
                if (templateRowHeight <= 0) {
                    templateRowHeight = 30.0;
                }

                // 清理表头显示：去掉 ${}
                List<XSLFTableCell> headerCells = headerRow.getCells();
                for (int c = 0; c < headerCells.size() && c < headers.length; c++) {
                    if (headers[c] != null) {
                        headerCells.get(c).setText(headers[c]);
                    }
                }

                // 复用已有样例行并补充新行；新增行克隆样例行格式
                for (int i = 0; i < data.size(); i++) {
                    Map<String, Object> rowData = data.get(i);
                    XSLFTableRow targetRow;
                    if (i < existingDataRows) {
                        targetRow = table.getRows().get(headerRowIndex + 1 + i);
                    } else {
                        targetRow = clonePptRow(table, sampleRowTemplate, headerRowIndex + 1 + i);
                        if (targetRow == null) {
                            continue;
                        }
                        targetRow.setHeight(templateRowHeight);
                        // 确保单元格数量与表头一致
                        int columns = headerCells.size();
                        while (targetRow.getCells().size() < columns) {
                            targetRow.addCell();
                        }
                    }
                    List<XSLFTableCell> cells = targetRow.getCells();
                    for (int c = 0; c < cells.size() && c < headers.length; c++) {
                        String header = headers[c];
                        Object value = isSequenceHeader(header) ? (i + 1) : rowData.get(header);
                        setPptCellText(cells.get(c), value == null ? "" : value.toString());
                    }
                }

                // 删除多余样例行
                int rowsToRemove = table.getRows().size() - (headerRowIndex + 1 + data.size());
                for (int k = 0; k < rowsToRemove; k++) {
                    table.removeRow(table.getRows().size() - 1);
                }
            }
            slideIndex++;
        }
    }

    private String getTextShapeText(XSLFTextShape textShape) {
        StringBuilder sb = new StringBuilder();
        for (XSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
            for (XSLFTextRun run : paragraph.getTextRuns()) {
                String text = run.getRawText();
                if (text != null) {
                    sb.append(text);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 设置 Word 单元格文本，同时尽量保留原 run 的字体格式。
     */
    private void setWordCellText(XWPFTableCell cell, String text) {
        if (cell.getParagraphs().isEmpty()) {
            cell.setText(text);
            return;
        }
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) {
            XWPFRun run = paragraph.createRun();
            run.setText(text);
            return;
        }
        XWPFRun firstRun = runs.get(0);
        firstRun.setText(text, 0);
        // 清除同一 run 内的多余 text 节点，避免旧文本残留
        for (int i = firstRun.getCTR().sizeOfTArray() - 1; i > 0; i--) {
            firstRun.getCTR().removeT(i);
        }
        for (int i = runs.size() - 1; i > 0; i--) {
            paragraph.removeRun(i);
        }
    }

    /**
     * 设置 PPT 单元格文本，同时尽量保留原 run 的字体格式。
     */
    private void setPptCellText(XSLFTableCell cell, String text) {
        List<XSLFTextParagraph> paragraphs = cell.getTextParagraphs();
        if (paragraphs.isEmpty()) {
            cell.setText(text);
            return;
        }
        XSLFTextParagraph paragraph = paragraphs.get(0);
        List<XSLFTextRun> runs = paragraph.getTextRuns();
        if (runs.isEmpty()) {
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(text);
            return;
        }
        runs.get(0).setText(text);
        for (int i = runs.size() - 1; i > 0; i--) {
            paragraph.removeTextRun(runs.get(i));
        }
    }

    /**
     * 在指定位置克隆 PPT 表格行，保留样例行的完整格式。
     */
    private XSLFTableRow clonePptRow(XSLFTable table, CTTableRow rowTemplate, int pos) {
        if (rowTemplate == null) {
            return table.insertRow(pos);
        }
        try {
            CTTable ctTable = table.getCTTable();
            CTTableRow newRowXml = ctTable.insertNewTr(pos);
            newRowXml.set(rowTemplate.copy());

            Method initMethod = XSLFTable.class.getDeclaredMethod("initializeRow", CTTableRow.class);
            initMethod.setAccessible(true);
            XSLFTableRow newRow = (XSLFTableRow) initMethod.invoke(table, newRowXml);

            Field rowsField = XSLFTable.class.getDeclaredField("_rows");
            rowsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<XSLFTableRow> rows = (List<XSLFTableRow>) rowsField.get(table);
            rows.add(pos, newRow);
            return newRow;
        } catch (Exception e) {
            log.warn("克隆 PPT 表格行失败，使用默认新增行", e);
            return table.insertRow(pos);
        }
    }

    private void replacePlaceholdersInPptTextShape(XSLFTextShape textShape, Map<String, Object> data, boolean clean) {
        for (XSLFTextParagraph paragraph : new ArrayList<>(textShape.getTextParagraphs())) {
            replacePptParagraphRuns(paragraph, data, clean);
        }
    }

    private void replacePptParagraphRuns(XSLFTextParagraph paragraph, Map<String, Object> data, boolean clean) {
        // 逐次处理每个占位符，每次处理后重新扫描，避免同一 run 内多个占位符位置偏移问题
        int maxIterations = 1000;
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            List<XSLFTextRun> runs = new ArrayList<>(paragraph.getTextRuns());
            if (runs.isEmpty()) {
                return;
            }

            StringBuilder runTextBuilder = new StringBuilder();
            for (XSLFTextRun run : runs) {
                String text = run.getRawText();
                if (text != null) {
                    runTextBuilder.append(text);
                }
            }
            String runText = runTextBuilder.toString();
            if (!runText.contains("${")) {
                return;
            }

            Matcher matcher = PLACEHOLDER_PATTERN.matcher(runText);
            if (!matcher.find()) {
                return;
            }

            int startChar = matcher.start();
            int endChar = matcher.end();
            String originalPlaceholder = runText.substring(startChar, endChar);

            // 建立字符位置到 run 索引的映射
            int[] charToRun = new int[runText.length()];
            int pos = 0;
            for (int i = 0; i < runs.size(); i++) {
                String text = runs.get(i).getRawText();
                int len = text == null ? 0 : text.length();
                for (int j = 0; j < len; j++) {
                    charToRun[pos++] = i;
                }
            }

            // 建立每个 run 在 runText 中的起始位置
            int[] runStartChars = new int[runs.size()];
            int curr = 0;
            for (int i = 0; i < runs.size(); i++) {
                runStartChars[i] = curr;
                String text = runs.get(i).getRawText();
                curr += text == null ? 0 : text.length();
            }

            int startRun = charToRun[startChar];
            int endRun = charToRun[endChar - 1];

            String replacement = replacePlaceholders(originalPlaceholder, data, clean);

            // 链式处理中未匹配占位符会被保留，继续替换会导致死循环，直接退出
            if (!clean && replacement.equals(originalPlaceholder)) {
                return;
            }

            XSLFTextRun startRunObj = runs.get(startRun);
            XSLFTextRun endRunObj = runs.get(endRun);
            String startRunText = startRunObj.getRawText();
            String endRunText = endRunObj.getRawText();
            if (startRunText == null) startRunText = "";
            if (endRunText == null) endRunText = "";

            String before = startChar > runStartChars[startRun]
                    ? startRunText.substring(0, startChar - runStartChars[startRun])
                    : "";
            String after = endChar < runStartChars[endRun] + endRunText.length()
                    ? endRunText.substring(endChar - runStartChars[endRun])
                    : "";

            // 保留 startRun 格式，替换占位符内容
            startRunObj.setText(before + replacement + after);

            // 移除占位符涉及的其它 run
            for (int r = endRun; r > startRun; r--) {
                paragraph.removeTextRun(runs.get(r));
            }
        }
        log.warn("PPT 段落占位符替换超过最大迭代次数，已跳过该段落以避免死循环");
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
