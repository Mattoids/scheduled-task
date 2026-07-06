package com.mattoid.scheduled.template;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class ExcelTemplateProcessor extends AbstractPoiTemplateProcessor {

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

            Sheet sheet = workbook.getSheetAt(0);
            int startRow = findStartRow(sheet);
            int columnCount = sheet.getRow(startRow) != null ? sheet.getRow(startRow).getLastCellNum() : 0;
            String[] headers = readHeaders(sheet, startRow, columnCount);

            // 处理占位符替换（标题行等）
            Map<String, Object> placeholderData = data.isEmpty() ? Collections.emptyMap() : data.get(0);
            replacePlaceholdersInSheet(sheet, placeholderData, cleanPlaceholders);

            // 在原表头行直接填充数据，表头行本身作为第一行数据
            if (!data.isEmpty() && headers != null) {
                for (int i = 0; i < data.size(); i++) {
                    Row row = sheet.getRow(startRow + i);
                    if (row == null) {
                        row = sheet.createRow(startRow + i);
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
                            copyCellStyle(sheet, startRow, c, cell);
                        }
                    }
                    // 清除该行中超出 headers 长度的旧单元格
                    for (int c = headers.length; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        if (cell != null) {
                            row.removeCell(cell);
                        }
                    }
                }
                // 删除超出数据范围的多余行
                int lastDataRow = startRow + data.size() - 1;
                for (int r = sheet.getLastRowNum(); r > lastDataRow; r--) {
                    Row row = sheet.getRow(r);
                    if (row != null) {
                        sheet.removeRow(row);
                    }
                }
            }

            File output = new File(outputFileName);
            try (FileOutputStream fos = new FileOutputStream(output)) {
                workbook.write(fos);
            }
            return output;
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
}
