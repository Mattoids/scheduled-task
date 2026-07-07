package com.mattoid.scheduled.service;

import lombok.extern.slf4j.Slf4j;
import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Service
public class ChartGenerationService {

    private static final int MAX_VISIBLE_CATEGORIES = 24;
    private static final int LONG_LABEL_THRESHOLD = 14;
    private static final int MEDIUM_LABEL_THRESHOLD = 8;
    private static final int MANY_CATEGORIES_THRESHOLD = 20;
    private static final int MEDIUM_CATEGORIES_THRESHOLD = 12;
    private static final int MAX_MERGED_LABEL_LENGTH = 20;

    /**
     * 根据二维表数据生成图表 PNG 文件。
     *
     * @param data      SQL 查询结果，每行一个 Map
     * @param chartType bar/line/pie/area/scatter/stacked_bar/doughnut，不区分大小写
     * @param title     图表标题
     * @return 生成的 PNG 文件，若数据不满足要求则返回 null
     */
    public File generateChart(List<Map<String, Object>> data, String chartType, String title) {
        return generateChart(data, chartType, title, true, "AUTO");
    }

    /**
     * 根据二维表数据生成图表 PNG 文件。
     *
     * @param data           SQL 查询结果，每行一个 Map
     * @param chartType      bar/line/pie/area/scatter/stacked_bar/doughnut，不区分大小写
     * @param title          图表标题
     * @param autoMerge      分类过多时是否自动合并相邻数据
     * @param labelRotation  X 轴标签旋转角度：AUTO / 0 / 45 / 90
     * @return 生成的 PNG 文件，若数据不满足要求则返回 null
     */
    public File generateChart(List<Map<String, Object>> data, String chartType, String title,
                              boolean autoMerge, String labelRotation) {
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
            List<Map<String, Object>> chartData = autoMerge ? maybeMergeData(data, categoryColumn) : data;
            switch (type) {
                case "PIE" -> generatePieChart(chartData, categoryColumn, valueColumns.get(0), chartTitle, tempFile, false);
                case "DOUGHNUT" -> generatePieChart(chartData, categoryColumn, valueColumns.get(0), chartTitle, tempFile, true);
                case "LINE" -> generateCategoryChart(chartData, categoryColumn, valueColumns, chartTitle, tempFile,
                        CategorySeries.CategorySeriesRenderStyle.Line, false, labelRotation);
                case "AREA" -> generateCategoryChart(chartData, categoryColumn, valueColumns, chartTitle, tempFile,
                        CategorySeries.CategorySeriesRenderStyle.Area, false, labelRotation);
                case "SCATTER" -> generateCategoryChart(chartData, categoryColumn, valueColumns, chartTitle, tempFile,
                        CategorySeries.CategorySeriesRenderStyle.Scatter, false, labelRotation);
                case "STACKED_BAR" -> generateCategoryChart(chartData, categoryColumn, valueColumns, chartTitle, tempFile,
                        CategorySeries.CategorySeriesRenderStyle.Bar, true, labelRotation);
                default -> generateCategoryChart(chartData, categoryColumn, valueColumns, chartTitle, tempFile,
                        CategorySeries.CategorySeriesRenderStyle.Bar, false, labelRotation);
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
                                       boolean stacked, String labelRotation) throws Exception {
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

        int maxLabelLength = categories.stream().mapToInt(String::length).max().orElse(0);
        int rotation = resolveLabelRotation(labelRotation, maxLabelLength, categories.size());
        if (rotation != 0) {
            chart.getStyler().setXAxisLabelRotation(rotation);
            if (Math.abs(rotation) == 90) {
                chart.getStyler().setPlotContentSize(0.70);
            }
            log.debug("图表 X 轴标签旋转: angle={}, maxLabelLength={}, categories={}", rotation, maxLabelLength, categories.size());
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

    private int resolveLabelRotation(String labelRotation, int maxLabelLength, int categoryCount) {
        if (labelRotation == null || "AUTO".equalsIgnoreCase(labelRotation.trim())) {
            if (maxLabelLength > LONG_LABEL_THRESHOLD || categoryCount > MANY_CATEGORIES_THRESHOLD) {
                return 90;
            } else if (maxLabelLength > MEDIUM_LABEL_THRESHOLD || categoryCount > MEDIUM_CATEGORIES_THRESHOLD) {
                return -45;
            }
            return 0;
        }
        try {
            return Integer.parseInt(labelRotation.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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

    /**
     * 当分类数量过多时，按相邻行分组合并，避免图表标签/柱形过于密集。
     * 分类标签拼接为首尾两项，数值列求和。
     */
    private List<Map<String, Object>> maybeMergeData(List<Map<String, Object>> data, String categoryColumn) {
        if (data == null || data.size() <= MAX_VISIBLE_CATEGORIES || categoryColumn == null) {
            return data;
        }
        int groupSize = (int) Math.ceil((double) data.size() / MAX_VISIBLE_CATEGORIES);
        List<Map<String, Object>> merged = new ArrayList<>();
        for (int i = 0; i < data.size(); i += groupSize) {
            int end = Math.min(i + groupSize, data.size());
            Map<String, Object> firstRow = data.get(i);
            Map<String, Object> mergedRow = new LinkedHashMap<>(firstRow);

            String firstCategory = formatValue(firstRow.get(categoryColumn));
            String lastCategory = formatValue(data.get(end - 1).get(categoryColumn));
            String mergedCategory = firstCategory.equals(lastCategory)
                    ? firstCategory
                    : firstCategory + "~" + lastCategory;
            if (mergedCategory.length() > MAX_MERGED_LABEL_LENGTH) {
                mergedCategory = firstCategory + "~" + lastCategory.substring(Math.max(0, lastCategory.length() - 6));
            }
            mergedRow.put(categoryColumn, mergedCategory);

            for (String col : firstRow.keySet()) {
                if (col.equals(categoryColumn)) {
                    continue;
                }
                if (!isNumericColumn(data, col)) {
                    continue;
                }
                double sum = 0;
                for (int j = i; j < end; j++) {
                    Number num = toNumber(data.get(j).get(col));
                    if (num != null) {
                        sum += num.doubleValue();
                    }
                }
                mergedRow.put(col, sum);
            }
            merged.add(mergedRow);
        }
        log.info("图表数据合并: 原始行数={}, 合并后行数={}, groupSize={}", data.size(), merged.size(), groupSize);
        return merged;
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
