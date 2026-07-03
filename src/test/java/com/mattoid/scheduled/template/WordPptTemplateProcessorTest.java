package com.mattoid.scheduled.template;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WordPptTemplateProcessorTest {

    @Test
    void wordTableExpandsWithMultiRowData() throws Exception {
        WordTemplateProcessor processor = new WordTemplateProcessor();

        File template = File.createTempFile("template", ".docx");
        template.deleteOnExit();
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(template)) {
            XWPFTable table = doc.createTable(2, 2);
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("${name}");
            headerRow.getCell(1).setText("${age}");

            XWPFTableRow sampleRow = table.getRow(1);
            sampleRow.getCell(0).setText("sample");
            sampleRow.getCell(1).setText("0");

            doc.write(fos);
        }

        File output = File.createTempFile("output", ".docx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25),
                Map.of("name", "Carol", "age", 35)
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        assertTrue(result.exists() && result.length() > 0);
        try (FileInputStream fis = new FileInputStream(result);
             XWPFDocument doc = new XWPFDocument(fis)) {
            assertEquals(1, doc.getTables().size());
            XWPFTable table = doc.getTables().get(0);
            assertEquals(4, table.getNumberOfRows(), "header + 3 data rows");

            XWPFTableRow header = table.getRow(0);
            assertEquals("name", header.getCell(0).getText());
            assertEquals("age", header.getCell(1).getText());

            XWPFTableRow row1 = table.getRow(1);
            assertEquals("Alice", row1.getCell(0).getText());
            assertEquals("30", row1.getCell(1).getText());

            XWPFTableRow row2 = table.getRow(2);
            assertEquals("Bob", row2.getCell(0).getText());
            assertEquals("25", row2.getCell(1).getText());

            XWPFTableRow row3 = table.getRow(3);
            assertEquals("Carol", row3.getCell(0).getText());
            assertEquals("35", row3.getCell(1).getText());
        }
    }

    @Test
    void wordTableExpandsWithHeaderOnly() throws Exception {
        WordTemplateProcessor processor = new WordTemplateProcessor();

        File template = File.createTempFile("template", ".docx");
        template.deleteOnExit();
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(template)) {
            XWPFTable table = doc.createTable(1, 2);
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("${name}");
            headerRow.getCell(1).setText("${age}");

            doc.write(fos);
        }

        File output = File.createTempFile("output", ".docx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        assertTrue(result.exists() && result.length() > 0);
        try (FileInputStream fis = new FileInputStream(result);
             XWPFDocument doc = new XWPFDocument(fis)) {
            XWPFTable table = doc.getTables().get(0);
            assertEquals(3, table.getNumberOfRows(), "header + 2 data rows");

            XWPFTableRow header = table.getRow(0);
            assertEquals("name", header.getCell(0).getText());
            assertEquals("age", header.getCell(1).getText());

            XWPFTableRow row1 = table.getRow(1);
            assertEquals("Alice", row1.getCell(0).getText());
            assertEquals("30", row1.getCell(1).getText());

            XWPFTableRow row2 = table.getRow(2);
            assertEquals("Bob", row2.getCell(0).getText());
            assertEquals("25", row2.getCell(1).getText());
        }
    }

    @Test
    void wordStaticPlaceholderOutsideTableIsReplaced() throws Exception {
        WordTemplateProcessor processor = new WordTemplateProcessor();

        File template = File.createTempFile("template", ".docx");
        template.deleteOnExit();
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(template)) {
            XWPFParagraph para = doc.createParagraph();
            para.createRun().setText("Report date: ${report_date}");

            // 单列表格只含一行时被视为普通占位符替换，不展开数据行
            XWPFTable table = doc.createTable(1, 2);
            XWPFTableRow row = table.getRow(0);
            row.getCell(0).setText("${name}");
            row.getCell(1).setText("${age}");

            doc.write(fos);
        }

        File output = File.createTempFile("output", ".docx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30, "report_date", "2026-07-03")
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             XWPFDocument doc = new XWPFDocument(fis)) {
            String paragraphText = doc.getParagraphs().get(0).getText();
            assertEquals("Report date: 2026-07-03", paragraphText);

            XWPFTable table = doc.getTables().get(0);
            assertEquals(1, table.getNumberOfRows());
            XWPFTableRow row = table.getRow(0);
            assertEquals("Alice", row.getCell(0).getText());
            assertEquals("30", row.getCell(1).getText());
        }
    }

    @Test
    void wordNonDataTableKeepsPlaceholderReplacement() throws Exception {
        WordTemplateProcessor processor = new WordTemplateProcessor();

        File template = File.createTempFile("template", ".docx");
        template.deleteOnExit();
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(template)) {
            XWPFTable table = doc.createTable(1, 2);
            XWPFTableRow row = table.getRow(0);
            row.getCell(0).setText("${report_date}");
            row.getCell(1).setText("${author}");
            doc.write(fos);
        }

        File output = File.createTempFile("output", ".docx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("report_date", "2026-07-03", "author", "Bob")
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             XWPFDocument doc = new XWPFDocument(fis)) {
            XWPFTable table = doc.getTables().get(0);
            XWPFTableRow row = table.getRow(0);
            assertEquals("2026-07-03", row.getCell(0).getText());
            assertEquals("Bob", row.getCell(1).getText());
        }
    }

    @Test
    void pptTableExpandsWithMultiRowData() throws Exception {
        PptTemplateProcessor processor = new PptTemplateProcessor();

        File template = File.createTempFile("template", ".pptx");
        template.deleteOnExit();
        try (XMLSlideShow ppt = new XMLSlideShow();
             FileOutputStream fos = new FileOutputStream(template)) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTable table = slide.createTable(2, 2);

            XSLFTableRow headerRow = table.getRows().get(0);
            headerRow.getCells().get(0).setText("${name}");
            headerRow.getCells().get(1).setText("${age}");

            XSLFTableRow sampleRow = table.getRows().get(1);
            sampleRow.getCells().get(0).setText("sample");
            sampleRow.getCells().get(1).setText("0");

            ppt.write(fos);
        }

        File output = File.createTempFile("output", ".pptx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        assertTrue(result.exists() && result.length() > 0);
        try (FileInputStream fis = new FileInputStream(result);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            XSLFSlide slide = ppt.getSlides().get(0);
            XSLFTable table = (XSLFTable) slide.getShapes().get(0);
            assertEquals(3, table.getNumberOfRows(), "header + 2 data rows");

            XSLFTableRow header = table.getRows().get(0);
            assertEquals("name", getCellText(header.getCells().get(0)));
            assertEquals("age", getCellText(header.getCells().get(1)));

            XSLFTableRow row1 = table.getRows().get(1);
            assertEquals("Alice", getCellText(row1.getCells().get(0)));
            assertEquals("30", getCellText(row1.getCells().get(1)));

            XSLFTableRow row2 = table.getRows().get(2);
            assertEquals("Bob", getCellText(row2.getCells().get(0)));
            assertEquals("25", getCellText(row2.getCells().get(1)));
        }
    }

    @Test
    void pptTableRemovesExtraSampleRows() throws Exception {
        PptTemplateProcessor processor = new PptTemplateProcessor();

        File template = File.createTempFile("template", ".pptx");
        template.deleteOnExit();
        try (XMLSlideShow ppt = new XMLSlideShow();
             FileOutputStream fos = new FileOutputStream(template)) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTable table = slide.createTable(4, 2);

            XSLFTableRow headerRow = table.getRows().get(0);
            headerRow.getCells().get(0).setText("${name}");
            headerRow.getCells().get(1).setText("${age}");

            table.getRows().get(1).getCells().get(0).setText("sample1");
            table.getRows().get(2).getCells().get(0).setText("sample2");
            table.getRows().get(3).getCells().get(0).setText("sample3");

            ppt.write(fos);
        }

        File output = File.createTempFile("output", ".pptx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            XSLFSlide slide = ppt.getSlides().get(0);
            XSLFTable table = (XSLFTable) slide.getShapes().get(0);
            assertEquals(3, table.getNumberOfRows(), "header + 2 data rows");

            XSLFTableRow row1 = table.getRows().get(1);
            assertEquals("Alice", getCellText(row1.getCells().get(0)));
            assertEquals("30", getCellText(row1.getCells().get(1)));

            XSLFTableRow row2 = table.getRows().get(2);
            assertEquals("Bob", getCellText(row2.getCells().get(0)));
            assertEquals("25", getCellText(row2.getCells().get(1)));
        }
    }

    @Test
    void pptSingleRowReplacesPlaceholdersInSampleRow() throws Exception {
        PptTemplateProcessor processor = new PptTemplateProcessor();

        File template = File.createTempFile("template", ".pptx");
        template.deleteOnExit();
        try (XMLSlideShow ppt = new XMLSlideShow();
             FileOutputStream fos = new FileOutputStream(template)) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTable table = slide.createTable(2, 2);

            XSLFTableRow headerRow = table.getRows().get(0);
            headerRow.getCells().get(0).setText("name");
            headerRow.getCells().get(1).setText("age");

            XSLFTableRow sampleRow = table.getRows().get(1);
            sampleRow.getCells().get(0).setText("${name}");
            sampleRow.getCells().get(1).setText("${age}");

            ppt.write(fos);
        }

        File output = File.createTempFile("output", ".pptx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(Map.of("name", "Alice", "age", 30));
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            XSLFSlide slide = ppt.getSlides().get(0);
            XSLFTable table = (XSLFTable) slide.getShapes().get(0);
            assertEquals(2, table.getNumberOfRows(), "single-row processing keeps table structure");

            XSLFTableRow header = table.getRows().get(0);
            assertEquals("name", getCellText(header.getCells().get(0)));
            assertEquals("age", getCellText(header.getCells().get(1)));

            XSLFTableRow row1 = table.getRows().get(1);
            assertEquals("Alice", getCellText(row1.getCells().get(0)));
            assertEquals("30", getCellText(row1.getCells().get(1)));
        }
    }

    @Test
    void wordTableExpandsWithPlainTextHeaders() throws Exception {
        WordTemplateProcessor processor = new WordTemplateProcessor();

        File template = File.createTempFile("template", ".docx");
        template.deleteOnExit();
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(template)) {
            XWPFTable table = doc.createTable(2, 2);
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("name");
            headerRow.getCell(1).setText("age");

            XWPFTableRow sampleRow = table.getRow(1);
            sampleRow.getCell(0).setText("sample");
            sampleRow.getCell(1).setText("0");

            doc.write(fos);
        }

        File output = File.createTempFile("output", ".docx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             XWPFDocument doc = new XWPFDocument(fis)) {
            XWPFTable table = doc.getTables().get(0);
            assertEquals(3, table.getNumberOfRows(), "header + 2 data rows");
            assertEquals("Alice", table.getRow(1).getCell(0).getText());
            assertEquals("Bob", table.getRow(2).getCell(0).getText());
        }
    }

    @Test
    void pptTableExpandsWithPlainTextHeaders() throws Exception {
        PptTemplateProcessor processor = new PptTemplateProcessor();

        File template = File.createTempFile("template", ".pptx");
        template.deleteOnExit();
        try (XMLSlideShow ppt = new XMLSlideShow();
             FileOutputStream fos = new FileOutputStream(template)) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTable table = slide.createTable(2, 2);

            XSLFTableRow headerRow = table.getRows().get(0);
            headerRow.getCells().get(0).setText("name");
            headerRow.getCells().get(1).setText("age");

            XSLFTableRow sampleRow = table.getRows().get(1);
            sampleRow.getCells().get(0).setText("sample");
            sampleRow.getCells().get(1).setText("0");

            ppt.write(fos);
        }

        File output = File.createTempFile("output", ".pptx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            XSLFSlide slide = ppt.getSlides().get(0);
            XSLFTable table = (XSLFTable) slide.getShapes().get(0);
            assertEquals(3, table.getNumberOfRows(), "header + 2 data rows");
            assertEquals("Alice", getCellText(table.getRows().get(1).getCells().get(0)));
            assertEquals("Bob", getCellText(table.getRows().get(2).getCells().get(0)));
        }
    }

    @Test
    void pptTableExpandsWithHeaderOnly() throws Exception {
        PptTemplateProcessor processor = new PptTemplateProcessor();

        File template = File.createTempFile("template", ".pptx");
        template.deleteOnExit();
        try (XMLSlideShow ppt = new XMLSlideShow();
             FileOutputStream fos = new FileOutputStream(template)) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTable table = slide.createTable(1, 2);

            XSLFTableRow headerRow = table.getRows().get(0);
            headerRow.getCells().get(0).setText("${name}");
            headerRow.getCells().get(1).setText("${age}");

            ppt.write(fos);
        }

        File output = File.createTempFile("output", ".pptx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        assertTrue(result.exists() && result.length() > 0);
        try (FileInputStream fis = new FileInputStream(result);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            XSLFSlide slide = ppt.getSlides().get(0);
            XSLFTable table = (XSLFTable) slide.getShapes().get(0);
            assertEquals(3, table.getNumberOfRows(), "header + 2 data rows");

            XSLFTableRow header = table.getRows().get(0);
            assertEquals("name", getCellText(header.getCells().get(0)));
            assertEquals("age", getCellText(header.getCells().get(1)));

            XSLFTableRow row1 = table.getRows().get(1);
            assertEquals("Alice", getCellText(row1.getCells().get(0)));
            assertEquals("30", getCellText(row1.getCells().get(1)));

            XSLFTableRow row2 = table.getRows().get(2);
            assertEquals("Bob", getCellText(row2.getCells().get(0)));
            assertEquals("25", getCellText(row2.getCells().get(1)));
        }
    }

    @Test
    void wordTablePreservesSampleRowFormatting() throws Exception {
        WordTemplateProcessor processor = new WordTemplateProcessor();

        File template = File.createTempFile("template", ".docx");
        template.deleteOnExit();
        int sampleHeight = 600;
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream fos = new FileOutputStream(template)) {
            XWPFTable table = doc.createTable(2, 2);
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("name");
            headerRow.getCell(1).setText("age");

            XWPFTableRow sampleRow = table.getRow(1);
            sampleRow.setHeight(sampleHeight);
            XWPFTableCell sampleCell = sampleRow.getCell(0);
            sampleCell.setText("sample");
            XWPFRun sampleRun = sampleCell.getParagraphs().get(0).getRuns().get(0);
            sampleRun.setBold(true);
            sampleRun.setFontSize(24);
            sampleRow.getCell(1).setText("0");

            doc.write(fos);
        }

        File output = File.createTempFile("output", ".docx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             XWPFDocument doc = new XWPFDocument(fis)) {
            XWPFTable table = doc.getTables().get(0);
            assertEquals(3, table.getNumberOfRows());

            XWPFTableRow row1 = table.getRow(1);
            assertEquals(sampleHeight, row1.getHeight());
            XWPFRun run1 = row1.getCell(0).getParagraphs().get(0).getRuns().get(0);
            assertTrue(run1.isBold());
            assertEquals(24, run1.getFontSize());

            XWPFTableRow row2 = table.getRow(2);
            assertEquals(sampleHeight, row2.getHeight());
            XWPFRun run2 = row2.getCell(0).getParagraphs().get(0).getRuns().get(0);
            assertTrue(run2.isBold());
            assertEquals(24, run2.getFontSize());
        }
    }

    @Test
    void pptTablePreservesSampleRowFormatting() throws Exception {
        PptTemplateProcessor processor = new PptTemplateProcessor();

        File template = File.createTempFile("template", ".pptx");
        template.deleteOnExit();
        double sampleHeight = 60.0;
        try (XMLSlideShow ppt = new XMLSlideShow();
             FileOutputStream fos = new FileOutputStream(template)) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTable table = slide.createTable(2, 2);

            XSLFTableRow headerRow = table.getRows().get(0);
            headerRow.getCells().get(0).setText("name");
            headerRow.getCells().get(1).setText("age");

            XSLFTableRow sampleRow = table.getRows().get(1);
            sampleRow.setHeight(sampleHeight);
            XSLFTableCell sampleCell = sampleRow.getCells().get(0);
            sampleCell.setText("sample");
            XSLFTextRun sampleRun = sampleCell.getTextParagraphs().get(0).getTextRuns().get(0);
            sampleRun.setBold(true);
            sampleRun.setFontSize(24.0);
            sampleRow.getCells().get(1).setText("0");

            ppt.write(fos);
        }

        File output = File.createTempFile("output", ".pptx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(
                Map.of("name", "Alice", "age", 30),
                Map.of("name", "Bob", "age", 25)
        );
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            XSLFSlide slide = ppt.getSlides().get(0);
            XSLFTable table = (XSLFTable) slide.getShapes().get(0);
            assertEquals(3, table.getNumberOfRows());

            XSLFTableRow row1 = table.getRows().get(1);
            assertEquals(sampleHeight, row1.getHeight(), 0.01);
            XSLFTextRun run1 = row1.getCells().get(0).getTextParagraphs().get(0).getTextRuns().get(0);
            assertTrue(run1.isBold());
            assertEquals(24.0, run1.getFontSize(), 0.01);

            XSLFTableRow row2 = table.getRows().get(2);
            assertEquals(sampleHeight, row2.getHeight(), 0.01);
            XSLFTextRun run2 = row2.getCells().get(0).getTextParagraphs().get(0).getTextRuns().get(0);
            assertTrue(run2.isBold());
            assertEquals(24.0, run2.getFontSize(), 0.01);
        }
    }

    @Test
    void pptSingleRowDoesNotExpandTable() throws Exception {
        PptTemplateProcessor processor = new PptTemplateProcessor();

        File template = File.createTempFile("template", ".pptx");
        template.deleteOnExit();
        try (XMLSlideShow ppt = new XMLSlideShow();
             FileOutputStream fos = new FileOutputStream(template)) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTable table = slide.createTable(2, 2);

            XSLFTableRow headerRow = table.getRows().get(0);
            headerRow.getCells().get(0).setText("${name}");
            headerRow.getCells().get(1).setText("${age}");

            XSLFTableRow sampleRow = table.getRows().get(1);
            sampleRow.getCells().get(0).setText("sample");
            sampleRow.getCells().get(1).setText("0");

            ppt.write(fos);
        }

        File output = File.createTempFile("output", ".pptx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(Map.of("name", "Alice", "age", 30));
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            XSLFSlide slide = ppt.getSlides().get(0);
            XSLFTable table = (XSLFTable) slide.getShapes().get(0);
            assertEquals(2, table.getNumberOfRows(), "single-row processing keeps original rows");

            XSLFTableRow header = table.getRows().get(0);
            assertEquals("Alice", getCellText(header.getCells().get(0)));
            assertEquals("30", getCellText(header.getCells().get(1)));

            XSLFTableRow row1 = table.getRows().get(1);
            assertEquals("sample", getCellText(row1.getCells().get(0)));
            assertEquals("0", getCellText(row1.getCells().get(1)));
        }
    }

    @Test
    void pptSingleRowHeaderOnlyReplacesPlaceholders() throws Exception {
        PptTemplateProcessor processor = new PptTemplateProcessor();

        File template = File.createTempFile("template", ".pptx");
        template.deleteOnExit();
        try (XMLSlideShow ppt = new XMLSlideShow();
             FileOutputStream fos = new FileOutputStream(template)) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTable table = slide.createTable(1, 2);

            XSLFTableRow headerRow = table.getRows().get(0);
            headerRow.getCells().get(0).setText("${name}");
            headerRow.getCells().get(1).setText("${age}");

            ppt.write(fos);
        }

        File output = File.createTempFile("output", ".pptx");
        output.deleteOnExit();
        List<Map<String, Object>> data = List.of(Map.of("name", "Alice", "age", 30));
        File result = processor.process(template, data, output.getAbsolutePath(), true);

        try (FileInputStream fis = new FileInputStream(result);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {
            XSLFSlide slide = ppt.getSlides().get(0);
            XSLFTable table = (XSLFTable) slide.getShapes().get(0);
            assertEquals(1, table.getNumberOfRows(), "only header row, placeholders replaced");

            XSLFTableRow header = table.getRows().get(0);
            assertEquals("Alice", getCellText(header.getCells().get(0)));
            assertEquals("30", getCellText(header.getCells().get(1)));
        }
    }

    private String getCellText(XSLFTableCell cell) {
        StringBuilder sb = new StringBuilder();
        cell.getTextParagraphs().forEach(p ->
                p.getTextRuns().forEach(r -> {
                    String text = r.getRawText();
                    if (text != null) sb.append(text);
                })
        );
        return sb.toString();
    }
}
