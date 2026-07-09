package com.mattoid.scheduled.service;

import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@Service
public class ExcelGenerationService {

    private static final int MAX_SHEET_NAME_LENGTH = 31;

    public File generateSingleExcel(List<Map<String, Object>> data, String outputPath, String sheetName) throws Exception {
        return generateSingleExcel(data, outputPath, sheetName, null);
    }

    public File generateSingleExcel(List<Map<String, Object>> data, String outputPath, String sheetName, String baseFilePath) throws Exception {
        return generateSingleExcel(data, outputPath, sheetName, baseFilePath, false);
    }

    public File generateSingleExcel(List<Map<String, Object>> data, String outputPath, String sheetName, String baseFilePath, boolean updateExistingSheet) throws Exception {
        return generateSingleExcel(data, outputPath, sheetName, baseFilePath, updateExistingSheet, -1);
    }

    public File generateSingleExcel(List<Map<String, Object>> data, String outputPath, String sheetName, String baseFilePath, boolean updateExistingSheet, int insertPosition) throws Exception {
        String resolvedSheetName = resolveSheetName(sheetName);
        return generateMergedExcel(List.of(new ExcelSheetSource(resolvedSheetName, data)), outputPath, baseFilePath, updateExistingSheet, insertPosition);
    }

    public File generateMergedExcel(List<ExcelSheetSource> sources, String outputPath) throws Exception {
        return generateMergedExcel(sources, outputPath, null);
    }

    public File generateMergedExcel(List<ExcelSheetSource> sources, String outputPath, String baseFilePath) throws Exception {
        return generateMergedExcel(sources, outputPath, baseFilePath, false);
    }

    public File generateMergedExcel(List<ExcelSheetSource> sources, String outputPath, String baseFilePath, boolean updateExistingSheet) throws Exception {
        return generateMergedExcel(sources, outputPath, baseFilePath, updateExistingSheet, -1);
    }

    public File generateMergedExcel(List<ExcelSheetSource> sources, String outputPath, String baseFilePath, boolean updateExistingSheet, int insertPosition) throws Exception {
        File output = new File(outputPath);
        File baseFile = StringUtils.hasText(baseFilePath) ? new File(baseFilePath) : null;
        boolean useBaseFile = baseFile != null && baseFile.exists();

        // 当输出路径与基础文件路径相同时，直接写入输出流会导致读取基础文件时被截断，
        // 因此先写入临时文件，再原子替换到目标位置。
        Path tempOutput = Files.createTempFile("excel_gen_", ".xlsx");
        List<String> newlyCreatedSheets = new ArrayList<>();
        try (Workbook workbook = useBaseFile ? WorkbookFactory.create(baseFile) : new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(tempOutput.toFile())) {
            Map<String, SheetInfo> sheets = new LinkedHashMap<>();
            int currentPosition = Math.max(0, insertPosition);

            for (ExcelSheetSource source : sources) {
                if (source.data() == null || source.data().isEmpty()) {
                    continue;
                }
                String resolvedSheetName = resolveSheetName(source.sheetName());
                boolean sheetExisted = workbook.getSheet(resolvedSheetName) != null;
                if (useBaseFile && sheetExisted) {
                    if (updateExistingSheet) {
                        workbook.removeSheetAt(workbook.getSheetIndex(resolvedSheetName));
                    } else {
                        log.info("Excel 追加模式跳过已存在 sheet: {}", resolvedSheetName);
                        continue;
                    }
                }
                writeDataToSheet(workbook, sheets, source.sheetName(), source.data());
                if (!sheetExisted || updateExistingSheet) {
                    newlyCreatedSheets.add(resolvedSheetName);
                }
            }

            applySheetPositions(workbook, newlyCreatedSheets, currentPosition);

            if (sheets.isEmpty() && workbook.getNumberOfSheets() == 0) {
                workbook.createSheet("Sheet1");
            }

            workbook.write(fos);
        }
        Files.move(tempOutput, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return output;
    }

    /**
     * 将 sourceFile 中的所有 sheet 按名称合并到 baseFile 中，baseFile 中已存在的 sheet 会被跳过。
     */
    public File appendSheetsToBaseFile(File baseFile, File sourceFile, String outputPath) throws Exception {
        return appendSheetsToBaseFile(baseFile, sourceFile, outputPath, false);
    }

    /**
     * 将 sourceFile 中的所有 sheet 按名称合并到 baseFile 中。
     *
     * @param updateExistingSheet 为 true 时，覆盖 baseFile 中已存在的同名 sheet；为 false 时跳过。
     */
    public File appendSheetsToBaseFile(File baseFile, File sourceFile, String outputPath, boolean updateExistingSheet) throws Exception {
        return appendSheetsToBaseFile(baseFile, sourceFile, outputPath, updateExistingSheet, -1);
    }

    /**
     * 将 sourceFile 中的所有 sheet 按名称合并到 baseFile 中。
     *
     * @param updateExistingSheet 为 true 时，覆盖 baseFile 中已存在的同名 sheet；为 false 时跳过。
     * @param insertPosition      新 sheet 插入位置，从 0 开始；负数表示追加到末尾。
     */
    public File appendSheetsToBaseFile(File baseFile, File sourceFile, String outputPath, boolean updateExistingSheet, int insertPosition) throws Exception {
        File output = new File(outputPath);
        boolean useBaseFile = baseFile != null && baseFile.exists();

        // 当输出路径与基础文件路径相同时，直接写入输出流会导致读取基础文件时被截断，
        // 因此先写入临时文件，再原子替换到目标位置。
        Path tempOutput = Files.createTempFile("excel_append_", ".xlsx");
        List<String> newlyCreatedSheets = new ArrayList<>();
        try (Workbook baseWorkbook = useBaseFile ? WorkbookFactory.create(baseFile) : new XSSFWorkbook();
             Workbook sourceWorkbook = WorkbookFactory.create(sourceFile);
             FileOutputStream fos = new FileOutputStream(tempOutput.toFile())) {
            int currentPosition = Math.max(0, insertPosition);
            for (int i = 0; i < sourceWorkbook.getNumberOfSheets(); i++) {
                Sheet sourceSheet = sourceWorkbook.getSheetAt(i);
                String sheetName = sourceSheet.getSheetName();
                boolean sheetExisted = baseWorkbook.getSheet(sheetName) != null;
                if (sheetExisted) {
                    if (updateExistingSheet) {
                        baseWorkbook.removeSheetAt(baseWorkbook.getSheetIndex(sheetName));
                    } else {
                        log.info("Excel 追加模式跳过已存在 sheet: {}", sheetName);
                        continue;
                    }
                }
                Sheet targetSheet = baseWorkbook.createSheet(sheetName);
                copySheet(sourceSheet, targetSheet);
                if (!sheetExisted || updateExistingSheet) {
                    newlyCreatedSheets.add(sheetName);
                }
            }
            applySheetPositions(baseWorkbook, newlyCreatedSheets, currentPosition);
            if (baseWorkbook.getNumberOfSheets() == 0) {
                baseWorkbook.createSheet("Sheet1");
            }
            baseWorkbook.write(fos);
        }
        Files.move(tempOutput, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return output;
    }

    private void copySheet(Sheet sourceSheet, Sheet targetSheet) {
        Workbook targetWorkbook = targetSheet.getWorkbook();
        Map<CellStyle, CellStyle> styleCache = new HashMap<>();
        for (Row sourceRow : sourceSheet) {
            Row targetRow = targetSheet.createRow(sourceRow.getRowNum());
            if (sourceRow.getRowStyle() != null) {
                CellStyle cached = styleCache.computeIfAbsent(sourceRow.getRowStyle(), s -> {
                    CellStyle targetStyle = targetWorkbook.createCellStyle();
                    targetStyle.cloneStyleFrom(s);
                    return targetStyle;
                });
                targetRow.setRowStyle(cached);
            }
            for (Cell sourceCell : sourceRow) {
                Cell targetCell = targetRow.createCell(sourceCell.getColumnIndex(), sourceCell.getCellType());
                copyCellValue(sourceCell, targetCell);
                if (sourceCell.getCellStyle() != null) {
                    CellStyle cached = styleCache.computeIfAbsent(sourceCell.getCellStyle(), s -> {
                        CellStyle targetStyle = targetWorkbook.createCellStyle();
                        targetStyle.cloneStyleFrom(s);
                        return targetStyle;
                    });
                    targetCell.setCellStyle(cached);
                }
            }
        }
        for (int i = 0; i < sourceSheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sourceSheet.getMergedRegion(i);
            targetSheet.addMergedRegion(region);
        }
    }

    private void copyCellValue(Cell source, Cell target) {
        switch (source.getCellType()) {
            case STRING -> target.setCellValue(source.getStringCellValue());
            case NUMERIC -> target.setCellValue(source.getNumericCellValue());
            case BOOLEAN -> target.setCellValue(source.getBooleanCellValue());
            case FORMULA -> target.setCellFormula(source.getCellFormula());
            case BLANK -> target.setBlank();
            default -> target.setCellValue(source.toString());
        }
    }

    private void applySheetPositions(Workbook workbook, List<String> newlyCreatedSheets, int startPosition) {
        if (newlyCreatedSheets.isEmpty() || startPosition < 0) {
            return;
        }
        int currentPosition = startPosition;
        for (String sheetName : newlyCreatedSheets) {
            int sheetIndex = workbook.getSheetIndex(sheetName);
            if (sheetIndex < 0) {
                continue;
            }
            int targetPosition = Math.min(currentPosition, workbook.getNumberOfSheets() - 1);
            if (sheetIndex != targetPosition) {
                workbook.setSheetOrder(sheetName, targetPosition);
            }
            currentPosition++;
        }
    }

    private void writeDataToSheet(Workbook workbook, Map<String, SheetInfo> sheets,
                                  String sheetName, List<Map<String, Object>> data) {
        String resolvedSheetName = resolveSheetName(sheetName);
        String safeName = uniqueSheetNameForWorkbook(workbook, sheets, resolvedSheetName);
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
        if (sheets.containsKey(sanitized) || workbook.getSheet(sanitized) == null) {
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

    private String resolveSheetName(String sheetName) {
        String resolved = PlaceholderUtils.replacePlaceholders(sheetName);
        return StringUtils.hasText(resolved) ? resolved : "Sheet1";
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
            String text = value.toString();
            if (isUrl(text)) {
                cell.setCellValue(text);
                Hyperlink link = cell.getSheet().getWorkbook().getCreationHelper()
                        .createHyperlink(HyperlinkType.URL);
                link.setAddress(text.trim());
                cell.setHyperlink(link);
            } else {
                cell.setCellValue(text);
            }
        }
    }

    private boolean isUrl(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://");
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
