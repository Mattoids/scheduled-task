package com.mattoid.scheduled.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

@Service
public class ExcelGenerationService {

    private static final int MAX_SHEET_NAME_LENGTH = 31;

    public File generateSingleExcel(List<Map<String, Object>> data, String outputPath) throws Exception {
        return generateMergedExcel(List.of(new ExcelSheetSource("Sheet1", data)), outputPath);
    }

    public File generateMergedExcel(List<ExcelSheetSource> sources, String outputPath) throws Exception {
        File output = new File(outputPath);
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(output)) {
            Map<String, SheetInfo> sheets = new LinkedHashMap<>();

            for (ExcelSheetSource source : sources) {
                if (source.data() == null || source.data().isEmpty()) {
                    continue;
                }
                writeDataToSheet(workbook, sheets, source.sheetName(), source.data());
            }

            if (sheets.isEmpty()) {
                workbook.createSheet("Sheet1");
            }

            workbook.write(fos);
        }
        return output;
    }

    private void writeDataToSheet(Workbook workbook, Map<String, SheetInfo> sheets,
                                  String sheetName, List<Map<String, Object>> data) {
        String safeName = uniqueSheetNameForWorkbook(workbook, sheets, sheetName);
        SheetInfo info = sheets.computeIfAbsent(safeName, k -> {
            Sheet sheet = workbook.getSheet(safeName);
            if (sheet == null) {
                sheet = workbook.createSheet(safeName);
            }
            return new SheetInfo(sheet, new ArrayList<>());
        });

        Sheet sheet = info.sheet;
        List<String> headers = info.headers;

        Set<String> existingHeaderSet = new LinkedHashSet<>(headers);
        List<String> newHeaders = new ArrayList<>();
        for (Map<String, Object> row : data) {
            for (String key : row.keySet()) {
                if (existingHeaderSet.add(key)) {
                    newHeaders.add(key);
                    headers.add(key);
                }
            }
        }

        int startRow = sheet.getLastRowNum() + 1;
        if (startRow == 0) {
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }
            startRow = 1;
        } else if (!newHeaders.isEmpty()) {
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                headerRow = sheet.createRow(0);
            }
            int startCol = headers.size() - newHeaders.size();
            for (int i = 0; i < newHeaders.size(); i++) {
                headerRow.createCell(startCol + i).setCellValue(newHeaders.get(i));
            }
        }

        for (int i = 0; i < data.size(); i++) {
            Row row = sheet.createRow(startRow + i);
            Map<String, Object> rowData = data.get(i);
            for (int c = 0; c < headers.size(); c++) {
                String header = headers.get(c);
                Object value = isSequenceHeader(header) ? startRow + i : rowData.get(header);
                setExcelCellValue(row.createCell(c), value);
            }
        }
    }

    private String uniqueSheetNameForWorkbook(Workbook workbook, Map<String, SheetInfo> sheets, String base) {
        String sanitized = sanitizeExcelSheetName(base);
        if (workbook.getSheet(sanitized) != null || !sheets.containsKey(sanitized)) {
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
        } while (sheets.containsKey(candidate) || workbook.getSheet(candidate) != null);
        return candidate;
    }

    private String sanitizeExcelSheetName(String name) {
        if (!StringUtils.hasText(name)) {
            return "Sheet1";
        }
        String sanitized = name.trim().replaceAll("[\\\\/:*?\\[\\]]", "_");
        if (sanitized.length() > MAX_SHEET_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_SHEET_NAME_LENGTH);
        }
        if ("History".equalsIgnoreCase(sanitized)) {
            sanitized = "History_";
        }
        return sanitized;
    }

    private boolean isSequenceHeader(String header) {
        if (header == null) {
            return false;
        }
        String trimmed = header.trim();
        return "序号".equals(trimmed) || "seq".equalsIgnoreCase(trimmed);
    }

    private void setExcelCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    public record ExcelSheetSource(String sheetName, List<Map<String, Object>> data) {
    }

    private static class SheetInfo {
        final Sheet sheet;
        final List<String> headers;

        SheetInfo(Sheet sheet, List<String> headers) {
            this.sheet = sheet;
            this.headers = headers;
        }
    }
}
