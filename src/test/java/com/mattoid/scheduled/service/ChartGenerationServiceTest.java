package com.mattoid.scheduled.service;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChartGenerationServiceTest {

    private final ChartGenerationService service = new ChartGenerationService();

    @Test
    void shouldMergeAdjacentRowsWhenTooManyCategories() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 1; i <= 26; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", "2026-01-" + String.format("%02d", i));
            row.put("amount", i);
            row.put("count", i * 10);
            data.add(row);
        }

        File chart = service.generateChart(data, "BAR", "每日数据");
        assertNotNull(chart);
        assertTrue(chart.exists());
        assertTrue(chart.length() > 0);
    }

    @Test
    void shouldNotMergeWhenFewCategories() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", "2026-01-" + String.format("%02d", i));
            row.put("amount", i);
            data.add(row);
        }

        File chart = service.generateChart(data, "BAR", "每日数据");
        assertNotNull(chart);
        assertTrue(chart.exists());
    }

    @Test
    void shouldRespectAutoMergeOff() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 1; i <= 26; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", "2026-01-" + String.format("%02d", i));
            row.put("amount", i);
            data.add(row);
        }

        File chart = service.generateChart(data, "BAR", "每日数据", false, "AUTO");
        assertNotNull(chart);
        assertTrue(chart.exists());
    }

    @Test
    void shouldRespectFixedLabelRotation() {
        List<Map<String, Object>> data = List.of(
                Map.of("product", "这是一个非常长的产品名称A", "sales", 100),
                Map.of("product", "这是一个非常长的产品名称B", "sales", 200),
                Map.of("product", "这是一个非常长的产品名称C", "sales", 150)
        );

        File chart = service.generateChart(data, "BAR", "产品销售", true, "45");
        assertNotNull(chart);
        assertTrue(chart.exists());
    }

    @Test
    void shouldApplyRgbBackgroundColor() {
        List<Map<String, Object>> data = List.of(
                Map.of("product", "A", "sales", 100),
                Map.of("product", "B", "sales", 200)
        );

        File chart = service.generateChart(data, "BAR", "产品销售", true, "AUTO", "rgba(255, 0, 0, 0.5)");
        assertNotNull(chart);
        assertTrue(chart.exists());
    }

    @Test
    void shouldApplyHexBackgroundColor() {
        List<Map<String, Object>> data = List.of(
                Map.of("product", "A", "sales", 100),
                Map.of("product", "B", "sales", 200)
        );

        File chart = service.generateChart(data, "BAR", "产品销售", true, "AUTO", "#FF0000");
        assertNotNull(chart);
        assertTrue(chart.exists());
    }

    @Test
    void shouldFallbackToTransparentForInvalidColor() {
        List<Map<String, Object>> data = List.of(
                Map.of("product", "A", "sales", 100),
                Map.of("product", "B", "sales", 200)
        );

        File chart = service.generateChart(data, "BAR", "产品销售", true, "AUTO", "not-a-color");
        assertNotNull(chart);
        assertTrue(chart.exists());
    }
}
