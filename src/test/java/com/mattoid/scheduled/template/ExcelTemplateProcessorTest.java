package com.mattoid.scheduled.template;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExcelTemplateProcessorTest {

    @Test
    void processProducesValidXlsx() throws Exception {
        ExcelTemplateProcessor processor = new ExcelTemplateProcessor();

        File template = File.createTempFile("template", ".xlsx");
        template.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(template)) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("${name}");
            row.createCell(1).setCellValue("${age}");
            wb.write(fos);
        }

        File output = File.createTempFile("output", ".xlsx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        assertTrue(result.exists() && result.length() > 0);
        try (FileInputStream fis = new FileInputStream(result);
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            assertNotNull(sheet);
        }
    }

    @Test
    void createsMultipleSheetsBySheetNameColumn() throws Exception {
        ExcelTemplateProcessor processor = new ExcelTemplateProcessor();

        File template = File.createTempFile("template", ".xlsx");
        template.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(template)) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("${month}");
            row.createCell(1).setCellValue("${amount}");
            wb.write(fos);
        }

        List<Map<String, Object>> data = List.of(
                Map.of("_sheet_name", "2026年01月", "month", "Jan", "amount", 100),
                Map.of("_sheet_name", "2026年02月", "month", "Feb", "amount", 200),
                Map.of("_sheet_name", "2026年01月", "month", "Jan2", "amount", 150)
        );

        File output = File.createTempFile("output", ".xlsx");
        output.deleteOnExit();
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             Workbook wb = WorkbookFactory.create(fis)) {
            assertEquals(2, wb.getNumberOfSheets(), "按 _sheet_name 分组成两个 sheet");
            Sheet sheet1 = wb.getSheet("2026年01月");
            Sheet sheet2 = wb.getSheet("2026年02月");
            assertNotNull(sheet1);
            assertNotNull(sheet2);

            // 表头行 + 两行数据
            assertEquals(2, sheet1.getLastRowNum(), "第一组应包含表头 + 两行数据");
            assertEquals("month", sheet1.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Jan", sheet1.getRow(1).getCell(0).getStringCellValue());
            assertEquals("Jan2", sheet1.getRow(2).getCell(0).getStringCellValue());

            assertEquals(1, sheet2.getLastRowNum(), "第二组应包含表头 + 一行数据");
            assertEquals("Feb", sheet2.getRow(1).getCell(0).getStringCellValue());
        }
    }

    @Test
    void usesDedicatedTemplateSheetAndHidesIt() throws Exception {
        ExcelTemplateProcessor processor = new ExcelTemplateProcessor();

        File template = File.createTempFile("template", ".xlsx");
        template.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(template)) {
            wb.createSheet("模板");
            Sheet templateSheet = wb.getSheet("模板");
            Row row = templateSheet.createRow(0);
            row.createCell(0).setCellValue("${month}");
            row.createCell(1).setCellValue("${amount}");
            wb.write(fos);
        }

        List<Map<String, Object>> data = List.of(
                Map.of("_sheet_name", "2026年03月", "month", "Mar", "amount", 300)
        );

        File output = File.createTempFile("output", ".xlsx");
        output.deleteOnExit();
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             Workbook wb = WorkbookFactory.create(fis)) {
            assertTrue(wb.isSheetHidden(wb.getSheetIndex("模板")), "模板 sheet 应被隐藏");
            Sheet dataSheet = wb.getSheet("2026年03月");
            assertNotNull(dataSheet);
            assertEquals("Mar", dataSheet.getRow(1).getCell(0).getStringCellValue());
        }
    }
}
