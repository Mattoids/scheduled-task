package com.mattoid.scheduled.service;

import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Service
public class ChartGenerationService {

    /**
     * 根据二维表数据生成图表 PNG 文件。
     *
     * @param data      SQL 查询结果，每行一个 Map
     * @param chartType bar/line/pie/area/scatter/stacked_bar/doughnut，不区分大小写
     * @param title     图表标题
     * @return 生成的 PNG 文件，若数据不满足要求则返回 null
     */
    public File generateChart(List<Map<String, Object>> data, String chartType, String title) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        String type = StringUtils.hasText(chartType) ? chartType.trim().toUpperCase() : "BAR";

        List<String> columns = new ArrayList<>(data.get(0).keySet());
        if (columns.isEmpty()) {
            return null;
        }

        // 分类列：优先选择非数值列；否则使用第一列
        String categoryColumn = columns.get(0);
        for (String col : columns) {
            if (!isNumericColumn(data, col)) {
                categoryColumn = col;
                break;
            }
        }

        // 数值列
        List<String> valueColumns = new ArrayList<>();
        for (String col : columns) {
            if (!col.equals(categoryColumn) && isNumericColumn(data, col)) {
                valueColumns.add(col);
            }
        }

        // 退化场景：只有一列且为数值列，使用行号作为分类
        if (valueColumns.isEmpty() && columns.size() == 1 && isNumericColumn(data, columns.get(0))) {
            categoryColumn = null;
            valueColumns.add(columns.get(0));
        }

        if (valueColumns.isEmpty()) {
            log.warn("数据中没有可用的数值列，无法生成图表");
            return null;
        }

        try {
            File tempFile = Files.createTempFile("chart_", ".png").toFile();
            String chartTitle = StringUtils.hasText(title) ? title : "数据图表";
            switch (type) {
                case "PIE" -> generatePieChart(data, categoryColumn, valueColumns.get(0), chartTitle, tempFile, false);
                case "DOUGHNUT" -> generatePieChart(data, categoryColumn, valueColumns.get(0), chartTitle, tempFile, true);
                case "LINE" -> generateCategoryChart(data, categoryColumn, valueColumns, chartTitle, tempFile,
                        CategorySeries.CategorySeriesRenderStyle.Line, false);
                case "AREA" -> generateCategoryChart(data, categoryColumn, valueColumns, chartTitle, tempFile,
                        CategorySeries.CategorySeriesRenderStyle.Area, false);
                case "SCATTER" -> generateCategoryChart(data, categoryColumn, valueColumns, chartTitle, tempFile,
                        CategorySeries.CategorySeriesRenderStyle.Scatter, false);
                case "STACKED_BAR" -> generateCategoryChart(data, categoryColumn, valueColumns, chartTitle, tempFile,
                        CategorySeries.CategorySeriesRenderStyle.Bar, true);
                default -> generateCategoryChart(data, categoryColumn, valueColumns, chartTitle, tempFile,
                        CategorySeries.CategorySeriesRenderStyle.Bar, false);
            }
            log.info("生成图表成功: type={}, title={}, file={}", type, chartTitle, tempFile.getAbsolutePath());
            return tempFile;
        } catch (Exception e) {
            log.error("生成图表失败: type={}, title={}", type, title, e);
            return null;
        }
    }

    private void generatePieChart(List<Map<String, Object>> data, String categoryColumn, String valueColumn,
                                  String title, File outputFile, boolean donut) throws Exception {
        PieChart chart = new PieChartBuilder().width(800).height(600).title(title).build();
        chart.getStyler().setLegendVisible(true);
        chart.getStyler().setChartTitleVisible(true);
        if (donut) {
            chart.getStyler().setDefaultSeriesRenderStyle(PieSeries.PieSeriesRenderStyle.Donut);
            chart.getStyler().setDonutThickness(0.4);
        }
        for (Map<String, Object> row : data) {
            String category = formatValue(row.get(categoryColumn));
            Number value = toNumber(row.get(valueColumn));
            if (value != null && StringUtils.hasText(category)) {
                chart.addSeries(category, value.doubleValue());
            }
        }
        BitmapEncoder.saveBitmap(chart, outputFile.getAbsolutePath().replace(".png", ""), BitmapEncoder.BitmapFormat.PNG);
    }

    private void generateCategoryChart(List<Map<String, Object>> data, String categoryColumn, List<String> valueColumns,
                                       String title, File outputFile, CategorySeries.CategorySeriesRenderStyle renderStyle,
                                       boolean stacked) throws Exception {
        CategoryChart chart = new CategoryChartBuilder()
                .width(900).height(600).title(title)
                .xAxisTitle(categoryColumn != null ? categoryColumn : "")
                .yAxisTitle("数值")
                .build();
        chart.getStyler().setLegendVisible(true);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setChartTitleVisible(true);
        if (stacked) {
            chart.getStyler().setStacked(true);
        }

        List<String> categories;
        if (categoryColumn != null) {
            categories = new ArrayList<>();
            for (Map<String, Object> row : data) {
                categories.add(formatValue(row.get(categoryColumn)));
            }
        } else {
            categories = IntStream.rangeClosed(1, data.size())
                    .mapToObj(String::valueOf)
                    .toList();
        }

        for (String valueColumn : valueColumns) {
            List<Number> values = new ArrayList<>();
            for (Map<String, Object> row : data) {
                Number num = toNumber(row.get(valueColumn));
                values.add(num != null ? num : 0);
            }
            CategorySeries series = chart.addSeries(valueColumn, categories, values);
            series.setChartCategorySeriesRenderStyle(renderStyle);
        }
        BitmapEncoder.saveBitmap(chart, outputFile.getAbsolutePath().replace(".png", ""), BitmapEncoder.BitmapFormat.PNG);
    }

    private boolean isNumericColumn(List<Map<String, Object>> data, String column) {
        for (Map<String, Object> row : data) {
            Object value = row.get(column);
            if (value == null) {
                continue;
            }
            if (!(value instanceof Number)) {
                return false;
            }
        }
        return true;
    }

    private Number toNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatValue(Object value) {
        return value == null ? "" : value.toString();
    }
}
