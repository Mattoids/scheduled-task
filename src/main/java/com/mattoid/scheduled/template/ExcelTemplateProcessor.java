package com.mattoid.scheduled.template;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
        try (FileInputStream fis = new FileInputStream(templateFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int startRow = findStartRow(sheet);
            int columnCount = sheet.getRow(startRow) != null ? sheet.getRow(startRow).getLastCellNum() : 0;
            String[] headers = readHeaders(sheet, startRow, columnCount);

            // 处理占位符替换
            replacePlaceholdersInSheet(sheet, data.isEmpty() ? null : data.get(0));

            // 在数据行追加数据
            if (!data.isEmpty() && headers != null) {
                for (int i = 0; i < data.size(); i++) {
                    Row row = sheet.createRow(startRow + 1 + i);
                    Map<String, Object> rowData = data.get(i);
                    for (int c = 0; c < headers.length; c++) {
                        Cell cell = row.createCell(c);
                        Object value = headers[c] != null ? rowData.get(headers[c]) : null;
                        setCellValue(cell, value);
                        copyCellStyle(sheet, startRow, c, cell);
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
