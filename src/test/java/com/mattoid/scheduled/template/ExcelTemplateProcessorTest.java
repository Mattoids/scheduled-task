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
}
