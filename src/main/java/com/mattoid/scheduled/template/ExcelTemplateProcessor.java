package com.mattoid.scheduled.template;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class ExcelTemplateProcessor extends AbstractPoiTemplateProcessor {

    private static final String SHEET_NAME_KEY = "_sheet_name";
    private static final Pattern INVALID_SHEET_NAME_CHAR_PATTERN = Pattern.compile("[\\\\/:*?\\[\\]]");
    private static final int MAX_SHEET_NAME_LENGTH = 31;

    @Override
    public boolean supports(String templateType) {
        return "EXCEL".equalsIgnoreCase(templateType);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName) throws Exception {
        return process(templateFile, data, outputFileName, true);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName, boolean cleanPlaceholders) throws Exception {
        try (FileInputStream fis = new FileInputStream(templateFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet templateSheet = findTemplateSheet(workbook);
            boolean dedicatedTemplate = isDedicatedTemplateSheet(templateSheet);

            if (hasSheetNameColumn(data)) {
                List<SheetGroup> groups = groupBySheetName(data);
                for (int i = 0; i < groups.size(); i++) {
                    SheetGroup group = groups.get(i);
                    Sheet target = createTargetSheet(workbook, templateSheet, dedicatedTemplate, i, group.name);
                    fillSheet(target, stripSheetNameColumn(group.rows), cleanPlaceholders);
                }
            } else {
                String singleSheetName = data.isEmpty() ? null : firstSheetName(data);
                Sheet target = createTargetSheet(workbook, templateSheet, dedicatedTemplate, 0,
                        singleSheetName != null ? singleSheetName : "数据");
                fillSheet(target, stripSheetNameColumn(data), cleanPlaceholders);
            }

            if (dedicatedTemplate) {
                int templateIndex = workbook.getSheetIndex(templateSheet);
                if (templateIndex >= 0) {
                    workbook.setSheetHidden(templateIndex, true);
                }
            }

            File output = new File(outputFileName);
            try (FileOutputStream fos = new FileOutputStream(output)) {
                workbook.write(fos);
            }
            return output;
        }
    }

    private Sheet findTemplateSheet(Workbook workbook) {
        for (Sheet sheet : workbook) {
            String name = sheet.getSheetName();
            if ("模板".equalsIgnoreCase(name) || "Template".equalsIgnoreCase(name)) {
                return sheet;
            }
        }
        return workbook.getSheetAt(0);
    }

    private boolean isDedicatedTemplateSheet(Sheet sheet) {
        String name = sheet.getSheetName();
        return "模板".equalsIgnoreCase(name) || "Template".equalsIgnoreCase(name);
    }

    private boolean hasSheetNameColumn(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        return data.get(0).containsKey(SHEET_NAME_KEY);
    }

    private String firstSheetName(List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            return null;
        }
        Object value = data.get(0).get(SHEET_NAME_KEY);
        return value == null ? null : value.toString();
    }

    private List<SheetGroup> groupBySheetName(List<Map<String, Object>> data) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : data) {
            Object value = row.get(SHEET_NAME_KEY);
            String name = value == null ? "" : value.toString();
            groups.computeIfAbsent(name, k -> new ArrayList<>()).add(row);
        }
        List<SheetGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            result.add(new SheetGroup(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private List<Map<String, Object>> stripSheetNameColumn(List<Map<String, Object>> data) {
        if (data == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>(data.size());
        for (Map<String, Object> row : data) {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            copy.remove(SHEET_NAME_KEY);
            result.add(copy);
        }
        return result;
    }

    private Sheet createTargetSheet(Workbook workbook, Sheet templateSheet, boolean dedicatedTemplate,
                                    int groupIndex, String sheetName) {
        String safeName = uniqueSheetName(workbook, sheetName);
        if (dedicatedTemplate) {
            Sheet cloned = workbook.cloneSheet(workbook.getSheetIndex(templateSheet));
            workbook.setSheetName(workbook.getSheetIndex(cloned), safeName);
            return cloned;
        }
        if (groupIndex == 0) {
            int idx = workbook.getSheetIndex(templateSheet);
            workbook.setSheetName(idx, safeName);
            return templateSheet;
        }
        Sheet cloned = workbook.cloneSheet(workbook.getSheetIndex(templateSheet));
        workbook.setSheetName(workbook.getSheetIndex(cloned), safeName);
        return cloned;
    }

    private void fillSheet(Sheet sheet, List<Map<String, Object>> data, boolean cleanPlaceholders) {
        int startRow = findStartRow(sheet);
        int columnCount = sheet.getRow(startRow) != null ? sheet.getRow(startRow).getLastCellNum() : 0;
        String[] headers = readHeaders(sheet, startRow, columnCount);

        // 清理表头行：去掉 ${}，保留字段名
        Row headerRow = sheet.getRow(startRow);
        if (headerRow != null && headers != null) {
            for (int c = 0; c < headers.length; c++) {
                Cell cell = headerRow.getCell(c);
                if (cell == null) {
                    continue;
                }
                if (headers[c] != null) {
                    setCellValue(cell, headers[c]);
                }
            }
        }

        // 表头以外的占位符（标题行等）用第一行数据替换
        Map<String, Object> placeholderData = data.isEmpty() ? Collections.emptyMap() : data.get(0);
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            if (r == startRow) {
                continue;
            }
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                String val = getCellStringValue(cell);
                if (val != null && val.contains("${")) {
                    setCellValue(cell, replacePlaceholders(val, placeholderData, cleanPlaceholders));
                }
            }
        }

        // 从表头下一行开始填充数据
        if (!data.isEmpty() && headers != null) {
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.getRow(startRow + 1 + i);
                if (row == null) {
                    row = sheet.createRow(startRow + 1 + i);
                }
                Map<String, Object> rowData = data.get(i);
                for (int c = 0; c < headers.length; c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) {
                        cell = row.createCell(c);
                    }
                    String header = headers[c];
                    Object value = isSequenceHeader(header) ? i + 1 : rowData.get(header);
                    setCellValue(cell, value);
                    if (i > 0) {
                        copyCellStyle(sheet, startRow + 1, c, cell);
                    }
                }
                for (int c = headers.length; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null) {
                        row.removeCell(cell);
                    }
                }
            }
            int lastDataRow = startRow + data.size();
            for (int r = sheet.getLastRowNum(); r > lastDataRow; r--) {
                Row row = sheet.getRow(r);
                if (row != null) {
                    sheet.removeRow(row);
                }
            }
        }
    }

    private int findStartRow(Sheet sheet) {
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                String val = getCellStringValue(cell);
                if (val != null && val.startsWith("${")) {
                    return r;
                }
            }
        }
        return 0;
    }

    private String[] readHeaders(Sheet sheet, int startRow, int columnCount) {
        Row row = sheet.getRow(startRow);
        if (row == null) return null;
        String[] headers = new String[columnCount];
        for (int c = 0; c < columnCount; c++) {
            Cell cell = row.getCell(c);
            String val = getCellStringValue(cell);
            if (val != null && val.startsWith("${") && val.endsWith("}")) {
                headers[c] = val.substring(2, val.length() - 1);
            } else {
                headers[c] = val;
            }
        }
        return headers;
    }

    private void copyCellStyle(Sheet sheet, int sourceRow, int sourceCol, Cell targetCell) {
        Row row = sheet.getRow(sourceRow);
        if (row == null) return;
        Cell sourceCell = row.getCell(sourceCol);
        if (sourceCell != null) {
            targetCell.setCellStyle(sourceCell.getCellStyle());
        }
    }

    private String uniqueSheetName(Workbook workbook, String base) {
        String sanitized = sanitizeSheetName(base);
        if (!sheetExists(workbook, sanitized)) {
            return sanitized;
        }
        int suffix = 1;
        String candidate;
        do {
            String suffixStr = "(" + suffix + ")";
            int maxBaseLength = MAX_SHEET_NAME_LENGTH - suffixStr.length();
            String basePart = sanitized.length() > maxBaseLength ? sanitized.substring(0, maxBaseLength) : sanitized;
            candidate = basePart + suffixStr;
            suffix++;
        } while (sheetExists(workbook, candidate));
        return candidate;
    }

    private boolean sheetExists(Workbook workbook, String name) {
        return workbook.getSheet(name) != null;
    }

    private String sanitizeSheetName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Sheet";
        }
        String sanitized = INVALID_SHEET_NAME_CHAR_PATTERN.matcher(name.trim()).replaceAll("_");
        if (sanitized.length() > MAX_SHEET_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_SHEET_NAME_LENGTH);
        }
        // Excel 保留名（不区分大小写）
        if ("History".equalsIgnoreCase(sanitized)) {
            sanitized = "History_";
        }
        return sanitized;
    }

    private record SheetGroup(String name, List<Map<String, Object>> rows) {
    }
}
