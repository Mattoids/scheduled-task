package com.mattoid.scheduled.template;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import com.mattoid.scheduled.service.ChartGenerationService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExcelTemplateProcessorTest {

    private ExcelTemplateProcessor createProcessor() {
        return new ExcelTemplateProcessor(mock(ChartGenerationService.class));
    }

    @Test
    void processProducesValidXlsx() throws Exception {
        ExcelTemplateProcessor processor = createProcessor();

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
        ExcelTemplateProcessor processor = createProcessor();

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
        ExcelTemplateProcessor processor = createProcessor();

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

    @Test
    void fillsMultipleDataAreasInSameSheet() throws Exception {
        ExcelTemplateProcessor processor = createProcessor();

        File template = File.createTempFile("multiarea", ".xlsx");
        template.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(template)) {
            Sheet sheet = wb.createSheet();
            Row header1 = sheet.createRow(0);
            header1.createCell(0).setCellValue("${seq}");
            header1.createCell(1).setCellValue("${channel}");
            Row sample1 = sheet.createRow(1);
            sample1.createCell(0).setCellValue(1);
            sample1.createCell(1).setCellValue("demo");

            Row header2 = sheet.createRow(3);
            header2.createCell(0).setCellValue("${channelTotal}");
            header2.createCell(1).setCellValue("${peopleTotal}");
            Row sample2 = sheet.createRow(4);
            sample2.createCell(0).setCellValue("demo");
            sample2.createCell(1).setCellValue(100);
            wb.write(fos);
        }

        File step1 = File.createTempFile("step1", ".xlsx");
        step1.deleteOnExit();
        List<Map<String, Object>> data1 = List.of(
                Map.of("seq", 1, "channel", "A"),
                Map.of("seq", 2, "channel", "B")
        );
        processor.process(template, data1, step1.getAbsolutePath(), false);

        File result = File.createTempFile("result", ".xlsx");
        result.deleteOnExit();
        List<Map<String, Object>> data2 = List.of(
                Map.of("channelTotal", "Total", "peopleTotal", "100")
        );
        processor.process(step1, data2, result.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            assertEquals(1, sheet.getRow(1).getCell(0).getNumericCellValue(), 0.001);
            assertEquals("A", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("Total", sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals("100", sheet.getRow(4).getCell(1).getStringCellValue());
        }
    }

    @Test
    void fillsMultipleDataAreasWithoutShiftingOtherColumns() throws Exception {
        ExcelTemplateProcessor processor = createProcessor();

        File template = File.createTempFile("multiarea_cols", ".xlsx");
        template.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(template)) {
            Sheet sheet = wb.createSheet();
            Row displayHeader1 = sheet.createRow(0);
            displayHeader1.createCell(0).setCellValue("ID");
            displayHeader1.createCell(1).setCellValue("Channel");
            displayHeader1.createCell(2).setCellValue("People");
            displayHeader1.createCell(3).setCellValue("Freq");
            Row header1 = sheet.createRow(1);
            header1.createCell(0).setCellValue("${seq}");
            header1.createCell(1).setCellValue("${channel}");
            header1.createCell(2).setCellValue("${people}");
            header1.createCell(3).setCellValue("${freq}");

            Row displayHeader2 = sheet.createRow(2);
            displayHeader2.createCell(8).setCellValue("Channel");
            displayHeader2.createCell(9).setCellValue("PeopleTotal");
            displayHeader2.createCell(10).setCellValue("FreqTotal");
            Row header2 = sheet.createRow(3);
            header2.createCell(8).setCellValue("${channelTotal}");
            header2.createCell(9).setCellValue("${peopleTotal}");
            header2.createCell(10).setCellValue("${freqTotal}");
            wb.write(fos);
        }

        File step1 = File.createTempFile("step1_cols", ".xlsx");
        step1.deleteOnExit();
        List<Map<String, Object>> data1 = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            data1.add(Map.of("seq", i, "channel", "C" + i, "people", i * 10, "freq", i * 2));
        }
        processor.process(template, data1, step1.getAbsolutePath(), false);

        File result = File.createTempFile("result_cols", ".xlsx");
        result.deleteOnExit();
        List<Map<String, Object>> data2 = List.of(
                Map.of("channelTotal", "Total1", "peopleTotal", 100, "freqTotal", 20),
                Map.of("channelTotal", "Total2", "peopleTotal", 200, "freqTotal", 40)
        );
        processor.process(step1, data2, result.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            // SQL1 data fills rows 1..5 in columns A-D (占位符行即首条数据行)
            assertEquals(1, sheet.getRow(1).getCell(0).getNumericCellValue(), 0.001);
            assertEquals("C1", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals(5, sheet.getRow(5).getCell(0).getNumericCellValue(), 0.001);
            assertEquals("C5", sheet.getRow(5).getCell(1).getStringCellValue());

            // SQL2 data should remain at its original position (rows 3..4 in columns I-K)
            // instead of being pushed down by SQL1 row expansion.
            assertEquals("Total1", sheet.getRow(3).getCell(8).getStringCellValue());
            assertEquals(100, sheet.getRow(3).getCell(9).getNumericCellValue(), 0.001);
            assertEquals(20, sheet.getRow(3).getCell(10).getNumericCellValue(), 0.001);
            assertEquals("Total2", sheet.getRow(4).getCell(8).getStringCellValue());
            assertEquals(200, sheet.getRow(4).getCell(9).getNumericCellValue(), 0.001);
            assertEquals(40, sheet.getRow(4).getCell(10).getNumericCellValue(), 0.001);
        }
    }

    @Test
    void shiftsSummaryRowDownAndUpdatesSumFormula() throws Exception {
        ExcelTemplateProcessor processor = createProcessor();

        File template = File.createTempFile("summary", ".xlsx");
        template.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(template)) {
            Sheet sheet = wb.createSheet();
            Row displayHeader = sheet.createRow(0);
            displayHeader.createCell(8).setCellValue("Channel");
            displayHeader.createCell(9).setCellValue("People");
            displayHeader.createCell(10).setCellValue("Freq");

            Row header = sheet.createRow(1);
            header.createCell(8).setCellValue("${channelTotal}");
            header.createCell(9).setCellValue("${peopleTotal}");
            header.createCell(10).setCellValue("${freqTotal}");

            Row summary = sheet.createRow(2);
            summary.createCell(8).setCellValue("汇总");
            summary.createCell(9).setCellFormula("SUM(J2)");
            summary.createCell(10).setCellFormula("SUM(K2)");
            wb.write(fos);
        }

        List<Map<String, Object>> data = List.of(
                Map.of("channelTotal", "A", "peopleTotal", 10, "freqTotal", 1),
                Map.of("channelTotal", "B", "peopleTotal", 20, "freqTotal", 2),
                Map.of("channelTotal", "C", "peopleTotal", 30, "freqTotal", 3)
        );

        File output = File.createTempFile("summary_out", ".xlsx");
        output.deleteOnExit();
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            // 数据应填充在 POI 第 1-3 行（Excel 第 2-4 行），占位符行即首条数据行
            assertEquals("A", sheet.getRow(1).getCell(8).getStringCellValue());
            assertEquals("B", sheet.getRow(2).getCell(8).getStringCellValue());
            assertEquals("C", sheet.getRow(3).getCell(8).getStringCellValue());

            // 汇总行应被下移到 POI 第 4 行（Excel 第 5 行）
            Row summaryRow = sheet.getRow(4);
            assertNotNull(summaryRow, "汇总行应存在");
            assertEquals("SUM(J2:J4)", summaryRow.getCell(9).getCellFormula());
            assertEquals("SUM(K2:K4)", summaryRow.getCell(10).getCellFormula());
        }
    }

    @Test
    void convertsUrlValuesToClickableHyperlinks() throws Exception {
        ExcelTemplateProcessor processor = createProcessor();

        File template = File.createTempFile("template_url", ".xlsx");
        template.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(template)) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("${title}");
            row.createCell(1).setCellValue("${url}");
            wb.write(fos);
        }

        File output = File.createTempFile("output_url", ".xlsx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("title", "Example", "url", "https://example.com/path"),
                Map.of("title", "Plain", "url", "not a link"),
                Map.of("title", "Http", "url", "http://example.org")
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            Cell linkCell = sheet.getRow(1).getCell(1);
            assertEquals("https://example.com/path", linkCell.getStringCellValue());
            assertNotNull(linkCell.getHyperlink());
            assertEquals("https://example.com/path", linkCell.getHyperlink().getAddress());

            Cell plainCell = sheet.getRow(2).getCell(1);
            assertEquals("not a link", plainCell.getStringCellValue());
            assertNull(plainCell.getHyperlink());

            Cell httpCell = sheet.getRow(3).getCell(1);
            assertNotNull(httpCell.getHyperlink());
            assertEquals("http://example.org", httpCell.getHyperlink().getAddress());
        }
    }

    @Test
    void excelChartPlaceholderReplacedWithPicture() throws Exception {
        ChartGenerationService realChartService = new ChartGenerationService();
        List<Map<String, Object>> data = List.of(
                Map.of("month", "Jan", "amount", 10),
                Map.of("month", "Feb", "amount", 20)
        );
        File chartFile = realChartService.generateChart(data, "BAR", "月度数据");
        assertNotNull(chartFile, "测试需要真实图表文件");

        ChartGenerationService mockService = mock(ChartGenerationService.class);
        when(mockService.generateChart(data, "BAR", "月度数据", true, "AUTO", (String) null)).thenReturn(chartFile);

        ExcelTemplateProcessor processor = new ExcelTemplateProcessor(mockService);

        File template = File.createTempFile("template", ".xlsx");
        template.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook();
             FileOutputStream fos = new FileOutputStream(template)) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(2);
            row.createCell(1).setCellValue("${chart}");
            wb.write(fos);
        }

        File output = File.createTempFile("output", ".xlsx");
        output.deleteOnExit();
        Map<String, Object> context = Map.of(
                "chartEnabled", 1,
                "chartType", "BAR",
                "chartTitle", "月度数据",
                "sqlCode", "SALES",
                "sqlName", "销售"
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true, context);

        try (FileInputStream fis = new FileInputStream(result);
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            boolean hasPicture = false;
            Drawing<?> drawing = sheet.getDrawingPatriarch();
            if (drawing instanceof org.apache.poi.xssf.usermodel.XSSFDrawing xssfDrawing) {
                for (org.apache.poi.xssf.usermodel.XSSFShape shape : xssfDrawing.getShapes()) {
                    if (shape instanceof org.apache.poi.xssf.usermodel.XSSFPicture) {
                        hasPicture = true;
                        break;
                    }
                }
            }
            assertTrue(hasPicture, "Excel 中应插入图表图片");
            // 占位符单元格应被清空
            assertTrue(sheet.getRow(2).getCell(1) == null || sheet.getRow(2).getCell(1).getCellType() == CellType.BLANK,
                    "占位符单元格应被清空");
        }
    }
}
