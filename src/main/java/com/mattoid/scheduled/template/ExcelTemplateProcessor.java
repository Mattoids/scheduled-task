package com.mattoid.scheduled.template;

import com.mattoid.scheduled.service.ChartGenerationService;
import com.mattoid.scheduled.util.PlaceholderUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ExcelTemplateProcessor extends AbstractPoiTemplateProcessor {

    private static final String SHEET_NAME_KEY = "_sheet_name";
    private static final Pattern INVALID_SHEET_NAME_CHAR_PATTERN = Pattern.compile("[\\\\/:*?\\[\\]]");
    private static final int MAX_SHEET_NAME_LENGTH = 31;

    private final ChartGenerationService chartGenerationService;

    public ExcelTemplateProcessor(ChartGenerationService chartGenerationService) {
        this.chartGenerationService = chartGenerationService;
    }

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
        return process(templateFile, data, outputFileName, cleanPlaceholders, null);
    }

    @Override
    public File process(File templateFile, List<Map<String, Object>> data, String outputFileName,
                        boolean cleanPlaceholders, Map<String, Object> context) throws Exception {
        try (FileInputStream fis = new FileInputStream(templateFile);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet templateSheet = findTemplateSheet(workbook);
            boolean dedicatedTemplate = isDedicatedTemplateSheet(templateSheet);

            if (hasSheetNameColumn(data)) {
                List<SheetGroup> groups = groupBySheetName(data);
                for (int i = 0; i < groups.size(); i++) {
                    SheetGroup group = groups.get(i);
                    Sheet target = createTargetSheet(workbook, templateSheet, dedicatedTemplate, i, group.name);
                    fillSheet(target, stripSheetNameColumn(group.rows), cleanPlaceholders, context);
                }
            } else {
                String singleSheetName = data.isEmpty() ? null : firstSheetName(data);
                if (singleSheetName == null) {
                    singleSheetName = resolveSheetNameFromContext(context);
                }
                Sheet target = createTargetSheet(workbook, templateSheet, dedicatedTemplate, 0,
                        singleSheetName != null ? singleSheetName : "数据");
                fillSheet(target, stripSheetNameColumn(data), cleanPlaceholders, context);
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
        String safeName = uniqueSheetName(workbook, sheetName, templateSheet);
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

    private void fillSheet(Sheet sheet, List<Map<String, Object>> data, boolean cleanPlaceholders, Map<String, Object> context) {
        // 查找所有数据区域（每处 ${} 表头或纯文本表头定义一个区域）
        Set<String> dataKeys = data.isEmpty() ? Collections.emptySet() : data.get(0).keySet();
        List<DataArea> areas = findDataAreas(sheet, dataKeys);

        // 先查找图表占位符单元格（避免后续表头清理将其覆盖）
        List<Cell> chartCells = findChartPlaceholderCells(sheet, context);
        Set<CellPosition> chartCellPositions = new HashSet<>();
        for (Cell cell : chartCells) {
            chartCellPositions.add(new CellPosition(cell.getRowIndex(), cell.getColumnIndex()));
        }

        // 根据数据列与区域表头的匹配度，选择本次要填充的区域
        DataArea selectedArea = selectBestMatchingArea(areas, data);

        // 非选中区域（以及无数据时所有区域）的占位符在链式处理中需要保留
        Set<Integer> protectedRows = new HashSet<>();
        if (!cleanPlaceholders) {
            for (DataArea area : areas) {
                if (selectedArea == null || area.headerRow != selectedArea.headerRow) {
                    if (area.hasDisplayHeader()) {
                        protectedRows.add(area.displayHeaderRow);
                    }
                    protectedRows.add(area.headerRow);
                }
            }
        }

        // 表头以外的占位符（标题行等）用第一行数据替换
        Map<String, Object> placeholderData = data.isEmpty() ? Collections.emptyMap() : data.get(0);
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            if (protectedRows.contains(r)) {
                continue;
            }
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                if (chartCellPositions.contains(new CellPosition(cell.getRowIndex(), cell.getColumnIndex()))) {
                    continue;
                }
                String val = getCellStringValue(cell);
                if (val != null && val.contains("${")) {
                    setCellValue(cell, replacePlaceholders(val, placeholderData, cleanPlaceholders));
                }
            }
        }

        // 填充选中的数据区域
        if (selectedArea != null && !data.isEmpty()) {
            fillDataArea(sheet, selectedArea, data, chartCellPositions);
        }

        // 插入图表图片
        if (!chartCells.isEmpty()) {
            File chartFile = resolveChartFile(data, context);
            if (chartFile != null && chartFile.exists()) {
                for (Cell cell : chartCells) {
                    insertChartImage(sheet, cell, chartFile);
                }
            }
        }
    }

    /**
     * 查找 Sheet 中所有数据区域。每个包含 ${key} 占位符的表头行视为一个区域的字段名行，
     * 其上方不含 ${} 的行为显示表头（可选）。区域边界到下一个字段名行为止。
     * 同一行中不相邻的占位符组会被拆分为独立区域，以便多 SQL 按列独立扩展。
     */
    private List<DataArea> findDataAreas(Sheet sheet, Set<String> dataKeys) {
        List<DataArea> areas = new ArrayList<>();
        int lastRowNum = sheet.getLastRowNum();
        DataArea previousArea = null;
        for (int r = 0; r <= lastRowNum; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String[] headers = readDataHeaders(row, dataKeys);
            if (headers == null || headers.length == 0) {
                continue;
            }

            // 区域结束行为下一个字段名行（不含显示表头），若不存在则为 sheet 末尾之后
            int endRow = lastRowNum + 1;
            for (int nextR = r + 1; nextR <= lastRowNum; nextR++) {
                Row nextRow = sheet.getRow(nextR);
                if (nextRow == null) {
                    continue;
                }
                String[] nextHeaders = readDataHeaders(nextRow, dataKeys);
                if (nextHeaders != null && nextHeaders.length > 0) {
                    endRow = nextR;
                    break;
                }
            }

            // 将同一行中不相邻的占位符列拆分为独立区域，并针对每个列组单独判断显示表头
            int startCol = -1;
            for (int c = 0; c <= headers.length; c++) {
                boolean hasHeader = c < headers.length && headers[c] != null;
                if (hasHeader && startCol < 0) {
                    startCol = c;
                } else if (!hasHeader && startCol >= 0) {
                    int groupEndCol = c - 1;
                    int candidateDisplayRow = r > 0 && isDisplayHeader(sheet.getRow(r - 1), startCol, groupEndCol) ? r - 1 : -1;
                    // 多区域场景：上一区域的数据行不应被误认为下一区域的显示表头（需列范围重叠）
                    if (candidateDisplayRow >= 0 && previousArea != null
                            && candidateDisplayRow >= previousArea.dataStartRow
                            && candidateDisplayRow < previousArea.endRow
                            && columnRangesOverlap(startCol, groupEndCol, previousArea.startCol, previousArea.endCol)) {
                        candidateDisplayRow = -1;
                    }
                    int displayHeaderRow = candidateDisplayRow;
                    // 若存在显示表头，则占位符所在行本身就是首条数据行；否则数据从占位符行下方开始
                    int dataStartRow = displayHeaderRow >= 0 ? r : r + 1;
                    areas.add(new DataArea(r, displayHeaderRow, dataStartRow, endRow, -1, startCol, groupEndCol, headers));
                    previousArea = areas.get(areas.size() - 1);
                    startCol = -1;
                }
            }
        }
        // 为每个区域单独检测其列范围内的汇总公式行，并调整区域边界
        List<DataArea> result = new ArrayList<>(areas.size());
        for (DataArea area : areas) {
            int summaryRow = findSummaryRow(sheet, area.dataStartRow, area.endRow, area.startCol, area.endCol);
            if (summaryRow >= 0) {
                result.add(new DataArea(area.headerRow, area.displayHeaderRow, area.dataStartRow, summaryRow,
                        summaryRow, area.startCol, area.endCol, area.headers));
            } else {
                result.add(area);
            }
        }
        return result;
    }

    /**
     * 查找数据区域内的汇总行：在 [dataStartRow, endRow) 范围内，
     * 第一个在 [startCol, endCol] 列范围内包含公式单元格的行视为汇总行。
     */
    private int findSummaryRow(Sheet sheet, int dataStartRow, int endRow, int startCol, int endCol) {
        for (int r = dataStartRow; r < endRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = startCol; c <= endCol; c++) {
                Cell cell = row.getCell(c);
                if (cell != null && cell.getCellType() == CellType.FORMULA) {
                    return r;
                }
            }
        }
        return -1;
    }

    /**
     * 更新汇总行公式，使其引用当前区域已填充的全部数据行。
     * 仅支持将单个单元格引用的 SUM 公式扩展为范围引用。
     */
    private void updateSummaryFormulas(Sheet sheet, DataArea area, int dataRows, int actualSummaryRow) {
        if (actualSummaryRow < 0 || dataRows <= 0) {
            return;
        }
        Row summaryRow = sheet.getRow(actualSummaryRow);
        if (summaryRow == null) {
            return;
        }
        int firstDataRowExcel = area.dataStartRow + 1; // Excel 行号为 1-based
        int lastDataRowExcel = area.dataStartRow + dataRows; // 含最后一条数据
        for (int c = area.startCol; c <= area.endCol; c++) {
            Cell cell = summaryRow.getCell(c);
            if (cell == null || cell.getCellType() != CellType.FORMULA) {
                continue;
            }
            String formula = cell.getCellFormula();
            String colRef = columnIndexToLetter(c);
            // 将形如 SUM(J4)、SUM($J$4) 或包含该列单个单元格引用的公式扩展为 SUM(J{first}:J{last})
            String expandedFormula = expandSingleCellRefsToRange(formula, c, firstDataRowExcel, lastDataRowExcel);
            if (!expandedFormula.equals(formula)) {
                cell.setCellFormula(expandedFormula);
            }
        }
    }

    /**
     * 将公式中指向本列（summaryCol）的单个单元格引用扩展为 [firstRow, lastRow] 的范围引用。
     * 当前仅处理 SUM 函数内的单个单元格引用。
     */
    private String expandSingleCellRefsToRange(String formula, int summaryCol, int firstRow, int lastRow) {
        if (formula == null || formula.isEmpty()) {
            return formula;
        }
        String colRef = columnIndexToLetter(summaryCol);
        String rangeRef = colRef + firstRow + ":" + colRef + lastRow;
        // 匹配 SUM( 后面跟本列单个单元格引用（支持绝对引用），然后 )
        String pattern = "SUM\\(\\s*\\$?" + colRef + "\\$?\\d+\\s*\\)";
        return formula.replaceAll(pattern, "SUM(" + rangeRef + ")");
    }

    /**
     * 将 0-based 列索引转换为 Excel 列字母（如 0->A, 8->I）。
     */
    private String columnIndexToLetter(int colIndex) {
        StringBuilder sb = new StringBuilder();
        int n = colIndex;
        do {
            sb.append((char) ('A' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.reverse().toString();
    }

    /**
     * 从一行单元格中提取数据表头：
     * 1. 只要行中包含任意 ${key} 占位符，即视为数据表头（ key 是否匹配后续选择区域时再决定）；
     * 2. 否则回退识别纯文本表头（所有非空单元格都对应数据字段）。
     */
    private String[] readDataHeaders(Row row, Set<String> dataKeys) {
        if (row == null) {
            return null;
        }
        int columnCount = row.getLastCellNum();
        if (columnCount <= 0) {
            return null;
        }
        List<String> cellTexts = new ArrayList<>(columnCount);
        boolean hasPlaceholder = false;
        for (int c = 0; c < columnCount; c++) {
            Cell cell = row.getCell(c);
            String val = getCellStringValue(cell);
            cellTexts.add(val);
            if (val != null && val.trim().startsWith("${") && val.trim().endsWith("}")) {
                hasPlaceholder = true;
            }
        }
        if (hasPlaceholder) {
            String[] headers = new String[columnCount];
            for (int c = 0; c < columnCount; c++) {
                String val = cellTexts.get(c);
                // 仅将包含 ${key} 的单元格识别为字段名，避免链式处理中将前一条 SQL 的数据值误判为表头
                if (val != null && val.trim().startsWith("${") && val.trim().endsWith("}")) {
                    headers[c] = extractHeaderKey(val);
                } else {
                    headers[c] = null;
                }
            }
            return headers;
        }
        return resolveDataHeaders(cellTexts, dataKeys);
    }

    private DataArea selectBestMatchingArea(List<DataArea> areas, List<Map<String, Object>> data) {
        if (areas.isEmpty()) {
            return null;
        }
        if (data.isEmpty() || data.get(0).isEmpty()) {
            return areas.get(0);
        }
        Set<String> dataKeys = data.get(0).keySet();
        DataArea best = null;
        int bestScore = -1;
        for (DataArea area : areas) {
            int score = 0;
            for (int c = area.startCol; c <= area.endCol && c < area.headers.length; c++) {
                String header = area.headers[c];
                if (header == null) {
                    continue;
                }
                if (isSequenceHeader(header) || dataKeys.contains(header)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = area;
            }
        }
        return best;
    }

    private void fillDataArea(Sheet sheet, DataArea area, List<Map<String, Object>> data,
                              Set<CellPosition> chartCellPositions) {
        int headerRowIdx = area.headerRow;
        int dataStartRow = area.dataStartRow;
        int endRow = area.endRow;
        String[] headers = area.headers;
        int startCol = area.startCol;
        int endCol = area.endCol;

        // 若存在显示表头，占位符所在行本身就是首条数据行，不需要再清理为字段名
        boolean placeholderAsFirstDataRow = dataStartRow == headerRowIdx;
        if (!placeholderAsFirstDataRow) {
            Row headerRow = sheet.getRow(headerRowIdx);
            if (headerRow != null) {
                for (int c = startCol; c <= endCol; c++) {
                    Cell cell = headerRow.getCell(c);
                    if (cell == null || chartCellPositions.contains(new CellPosition(cell.getRowIndex(), cell.getColumnIndex()))) {
                        continue;
                    }
                    if (headers[c] != null) {
                        setCellValue(cell, headers[c]);
                    }
                }
            }
        }

        int rowsNeeded = data.size();
        // 区域内可用数据行数：当存在汇总行时，区域结束行已被调整为汇总行，因此可用行不含汇总行
        int availableRows = endRow - dataStartRow;

        // 在可能插入新行之前，先捕获样例数据行的单元格样式，用于后续新增行复制格式
        CellStyle[] sampleStyles = captureSampleStyles(sheet, dataStartRow, startCol, endCol);

        // 若数据行数超过当前区域可用行数，将本区域列范围内的内容向下平移，不影响其他列
        int actualSummaryRow = area.summaryRow;
        if (rowsNeeded > availableRows && endRow <= sheet.getLastRowNum()) {
            int rowsToInsert = rowsNeeded - availableRows;
            shiftCellsInColumnRange(sheet, endRow, sheet.getLastRowNum(), startCol, endCol, rowsToInsert);
            if (area.hasSummaryRow()) {
                actualSummaryRow = area.summaryRow + rowsToInsert;
            }
        }

        // 填充数据行
        for (int i = 0; i < data.size(); i++) {
            int targetRowIdx = dataStartRow + i;
            Row row = sheet.getRow(targetRowIdx);
            if (row == null) {
                row = sheet.createRow(targetRowIdx);
            }
            Map<String, Object> rowData = data.get(i);
            for (int c = startCol; c <= endCol; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    cell = row.createCell(c);
                }
                if (chartCellPositions.contains(new CellPosition(cell.getRowIndex(), cell.getColumnIndex()))) {
                    continue;
                }
                String header = headers[c];
                Object value = isSequenceHeader(header) ? i + 1 : rowData.get(header);
                setCellValue(cell, value);
                // 将样例数据行的格式复制到当前单元格，保证新增行也有边框等样式
                if (sampleStyles[c - startCol] != null) {
                    cell.setCellStyle(sampleStyles[c - startCol]);
                }
            }
        }

        // 单区域模板时，清理数据下方的空行；多区域时仅清理本区域列范围，保留其他区域的数据行
        if (endRow > sheet.getLastRowNum()) {
            int lastDataRow = dataStartRow + data.size() - 1;
            for (int r = sheet.getLastRowNum(); r > lastDataRow; r--) {
                // 保留汇总行，由后续公式更新逻辑处理
                if (area.hasSummaryRow() && r == actualSummaryRow) {
                    continue;
                }
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                boolean hasCellsOutsideArea = false;
                for (Cell cell : row) {
                    int col = cell.getColumnIndex();
                    if (col < startCol || col > endCol) {
                        hasCellsOutsideArea = true;
                        break;
                    }
                }
                if (hasCellsOutsideArea) {
                    for (int c = startCol; c <= endCol; c++) {
                        Cell cell = row.getCell(c);
                        if (cell != null) {
                            row.removeCell(cell);
                        }
                    }
                } else {
                    sheet.removeRow(row);
                }
            }
        }

        // 更新汇总行公式，使其覆盖本次填充的全部数据行
        updateSummaryFormulas(sheet, area, data.size(), actualSummaryRow);
    }

    /**
     * 仅将指定列范围内的单元格向下平移，保留其他列位置不变。
     */
    private void shiftCellsInColumnRange(Sheet sheet, int startRow, int endRow, int startCol, int endCol, int rowsToInsert) {
        if (rowsToInsert <= 0 || startRow > endRow) {
            return;
        }
        for (int r = endRow; r >= startRow; r--) {
            Row sourceRow = sheet.getRow(r);
            if (sourceRow == null) {
                continue;
            }
            int targetRowIdx = r + rowsToInsert;
            Row targetRow = sheet.getRow(targetRowIdx);
            if (targetRow == null) {
                targetRow = sheet.createRow(targetRowIdx);
            }
            for (int c = startCol; c <= endCol; c++) {
                Cell sourceCell = sourceRow.getCell(c);
                if (sourceCell != null) {
                    Cell existingCell = targetRow.getCell(c);
                    if (existingCell != null) {
                        targetRow.removeCell(existingCell);
                    }
                    Cell targetCell = targetRow.createCell(c, sourceCell.getCellType());
                    cloneCellValue(sourceCell, targetCell);
                    targetCell.setCellStyle(sourceCell.getCellStyle());
                    sourceRow.removeCell(sourceCell);
                }
            }
        }
    }

    private void cloneCellValue(Cell source, Cell target) {
        switch (source.getCellType()) {
            case STRING -> target.setCellValue(source.getStringCellValue());
            case NUMERIC -> target.setCellValue(source.getNumericCellValue());
            case BOOLEAN -> target.setCellValue(source.getBooleanCellValue());
            case FORMULA -> target.setCellFormula(source.getCellFormula());
            case BLANK -> target.setBlank();
            default -> target.setCellValue(source.toString());
        }
    }

    private boolean isDisplayHeader(Row row, int startCol, int endCol) {
        if (row == null) {
            return false;
        }
        boolean hasContent = false;
        for (int c = startCol; c <= endCol; c++) {
            Cell cell = row.getCell(c);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                continue;
            }
            hasContent = true;
            String val = getCellStringValue(cell);
            if (val != null && val.contains("${")) {
                return false;
            }
            // 显示表头的非空单元格应为文本（避免将上一 SQL 的数据行误判为表头）
            if (cell.getCellType() != CellType.STRING) {
                return false;
            }
        }
        return hasContent;
    }

    private boolean columnRangesOverlap(int startCol1, int endCol1, int startCol2, int endCol2) {
        return startCol1 <= endCol2 && startCol2 <= endCol1;
    }

    /**
     * 捕获样例数据行的单元格样式，用于后续新增行复制格式。
     */
    private CellStyle[] captureSampleStyles(Sheet sheet, int dataStartRow, int startCol, int endCol) {
        CellStyle[] styles = new CellStyle[endCol - startCol + 1];
        Row row = sheet.getRow(dataStartRow);
        if (row == null) {
            return styles;
        }
        for (int c = startCol; c <= endCol; c++) {
            Cell cell = row.getCell(c);
            if (cell != null) {
                styles[c - startCol] = cell.getCellStyle();
            }
        }
        return styles;
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
        return uniqueSheetName(workbook, base, null);
    }

    private String uniqueSheetName(Workbook workbook, String base, Sheet excludeSheet) {
        String sanitized = sanitizeSheetName(base);
        if (!sheetExists(workbook, sanitized, excludeSheet)) {
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
        } while (sheetExists(workbook, candidate, excludeSheet));
        return candidate;
    }

    private boolean sheetExists(Workbook workbook, String name) {
        return workbook.getSheet(name) != null;
    }

    private boolean sheetExists(Workbook workbook, String name, Sheet excludeSheet) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            return false;
        }
        return excludeSheet == null || sheet != excludeSheet;
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

    private List<Cell> findChartPlaceholderCells(Sheet sheet, Map<String, Object> context) {
        List<Cell> result = new ArrayList<>();
        if (context == null || !isChartEnabled(context)) {
            return result;
        }
        String sqlCode = context.get("sqlCode") == null ? null : context.get("sqlCode").toString();
        for (Row row : sheet) {
            if (row == null) {
                continue;
            }
            for (Cell cell : row) {
                String val = getCellStringValue(cell);
                if (containsChartPlaceholder(val, sqlCode)) {
                    result.add(cell);
                }
            }
        }
        return result;
    }

    private boolean isChartEnabled(Map<String, Object> context) {
        Object enabledObj = context.get("chartEnabled");
        return enabledObj instanceof Integer enabled && enabled == 1;
    }

    private File resolveChartFile(List<Map<String, Object>> data, Map<String, Object> context) {
        if (context == null || data == null || data.isEmpty()) {
            return null;
        }
        File chartFile = (File) context.get("chartFile");
        if (chartFile != null && chartFile.exists()) {
            return chartFile;
        }
        String chartType = context.get("chartType") == null ? null : context.get("chartType").toString();
        String chartTitle = context.get("chartTitle") == null ? null : context.get("chartTitle").toString();
        String chartBackgroundColor = context.get("chartBackgroundColor") == null ? null : context.get("chartBackgroundColor").toString();
        if (!StringUtils.hasText(chartTitle)) {
            Object sqlName = context.get("sqlName");
            chartTitle = sqlName == null ? "数据图表" : sqlName.toString();
        }
        return chartGenerationService.generateChart(data, chartType, chartTitle, true, "AUTO", chartBackgroundColor);
    }

    private void insertChartImage(Sheet sheet, Cell cell, File imageFile) {
        try {
            Workbook workbook = sheet.getWorkbook();
            byte[] imageBytes;
            try (FileInputStream fis = new FileInputStream(imageFile)) {
                imageBytes = IOUtils.toByteArray(fis);
            }
            int pictureIdx = workbook.addPicture(imageBytes, Workbook.PICTURE_TYPE_PNG);
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
            anchor.setCol1(cell.getColumnIndex());
            anchor.setRow1(cell.getRowIndex());
            anchor.setCol2(cell.getColumnIndex() + 8);
            anchor.setRow2(cell.getRowIndex() + 12);
            anchor.setDx1(0);
            anchor.setDy1(0);
            anchor.setDx2(0);
            anchor.setDy2(0);
            drawing.createPicture(anchor, pictureIdx);
            // 清空占位符文本
            cell.setBlank();
            log.debug("Excel 插入图表: {}", imageFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Excel 插入图表失败", e);
        }
    }

    private String resolveSheetNameFromContext(Map<String, Object> context) {
        if (context == null) {
            return null;
        }
        Object sheetNameObj = context.get("excelSheetName");
        String sheetName = sheetNameObj == null ? null : sheetNameObj.toString();
        if (!StringUtils.hasText(sheetName)) {
            Object sqlName = context.get("sqlName");
            sheetName = sqlName == null ? null : sqlName.toString();
        }
        return StringUtils.hasText(sheetName) ? PlaceholderUtils.replacePlaceholders(sheetName) : null;
    }

    private record CellPosition(int row, int col) {
    }

    private record SheetGroup(String name, List<Map<String, Object>> rows) {
    }

    private record DataArea(int headerRow, int displayHeaderRow, int dataStartRow, int endRow,
                            int summaryRow, int startCol, int endCol, String[] headers) {
        boolean hasDisplayHeader() {
            return displayHeaderRow >= 0;
        }

        boolean hasSummaryRow() {
            return summaryRow >= 0;
        }

        boolean containsCol(int col) {
            return col >= startCol && col <= endCol;
        }
    }
}
