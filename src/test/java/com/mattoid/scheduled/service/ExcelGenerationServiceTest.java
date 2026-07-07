package com.mattoid.scheduled.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExcelGenerationServiceTest {

    private final ExcelGenerationService service = new ExcelGenerationService();

    @Test
    void shouldMergeMultipleSqlsIntoSameSheet() throws Exception {
        File output = File.createTempFile("excel_same_sheet_", ".xlsx");
        output.deleteOnExit();

        List<Map<String, Object>> data1 = new ArrayList<>();
        data1.add(row("name", "A", "value", 10));
        data1.add(row("name", "B", "value", 20));

        List<Map<String, Object>> data2 = new ArrayList<>();
        data2.add(row("name", "C", "value", 30));
        data2.add(row("name", "D", "value", 40));

        List<ExcelGenerationService.ExcelSheetSource> sources = List.of(
                new ExcelGenerationService.ExcelSheetSource("Sheet1", data1),
                new ExcelGenerationService.ExcelSheetSource("Sheet1", data2)
        );

        File result = service.generateMergedExcel(sources, output.getAbsolutePath());
        assertNotNull(result);
        assertTrue(result.exists());

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(result))) {
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals("Sheet1", sheet.getSheetName());
            assertEquals(5, sheet.getLastRowNum() + 1);
            assertEquals("name", stringCellValue(sheet.getRow(0).getCell(0)));
            assertEquals("value", stringCellValue(sheet.getRow(0).getCell(1)));
            assertEquals("A", stringCellValue(sheet.getRow(1).getCell(0)));
            assertEquals(40.0, numericCellValue(sheet.getRow(4).getCell(1)), 0.001);
        }
    }

    @Test
    void shouldWriteMultipleSqlsToMultipleSheets() throws Exception {
        File output = File.createTempFile("excel_multi_sheet_", ".xlsx");
        output.deleteOnExit();

        List<Map<String, Object>> data1 = List.of(
                row("id", 1, "score", 90),
                row("id", 2, "score", 80)
        );
        List<Map<String, Object>> data2 = List.of(
                row("id", 3, "score", 70)
        );

        List<ExcelGenerationService.ExcelSheetSource> sources = List.of(
                new ExcelGenerationService.ExcelSheetSource("SQL-A", data1),
                new ExcelGenerationService.ExcelSheetSource("SQL-B", data2)
        );

        File result = service.generateMergedExcel(sources, output.getAbsolutePath());
        assertNotNull(result);

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(result))) {
            assertEquals(2, workbook.getNumberOfSheets());
            Sheet sheet0 = workbook.getSheetAt(0);
            assertEquals("SQL-A", sheet0.getSheetName());
            assertEquals(3, sheet0.getLastRowNum() + 1);

            Sheet sheet1 = workbook.getSheetAt(1);
            assertEquals("SQL-B", sheet1.getSheetName());
            assertEquals(2, sheet1.getLastRowNum() + 1);
        }
    }

    @Test
    void shouldSupportSingleSqlWithMultipleSheetNameSources() throws Exception {
        File output = File.createTempFile("excel_single_sql_multi_sheet_", ".xlsx");
        output.deleteOnExit();

        List<Map<String, Object>> dataQ1 = List.of(
                row("month", "Jan", "amount", 100),
                row("month", "Feb", "amount", 200)
        );
        List<Map<String, Object>> dataQ2 = List.of(
                row("month", "Mar", "amount", 300)
        );

        List<ExcelGenerationService.ExcelSheetSource> sources = List.of(
                new ExcelGenerationService.ExcelSheetSource("Q1", dataQ1),
                new ExcelGenerationService.ExcelSheetSource("Q2", dataQ2)
        );

        File result = service.generateMergedExcel(sources, output.getAbsolutePath());
        assertNotNull(result);

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(result))) {
            assertEquals(2, workbook.getNumberOfSheets());
            assertNotNull(workbook.getSheet("Q1"));
            assertNotNull(workbook.getSheet("Q2"));
            assertEquals(3, workbook.getSheet("Q1").getLastRowNum() + 1);
            assertEquals(2, workbook.getSheet("Q2").getLastRowNum() + 1);
        }
    }

    @Test
    void shouldGenerateSingleExcelWithDefaultSheetWhenEmpty() throws Exception {
        File output = File.createTempFile("excel_empty_", ".xlsx");
        output.deleteOnExit();

        File result = service.generateSingleExcel(List.of(), output.getAbsolutePath(), null);
        assertNotNull(result);

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(result))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("Sheet1", workbook.getSheetAt(0).getSheetName());
        }
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private String stringCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return cell.getStringCellValue();
    }

    private double numericCellValue(Cell cell) {
        if (cell == null) {
            return Double.NaN;
        }
        return cell.getNumericCellValue();
    }
}
