package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.task.SqlExecutor;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExcelLoopHelperTest {

    private SqlExecutor stubExecutor() {
        return new SqlExecutor(null) {
            @Override
            public List<Map<String, Object>> executeQuery(Long datasourceId, String sql, Map<String, Object> params) {
                Object loopValue = params.get("loopValue");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("month", loopValue.toString());
                return List.of(row);
            }
        };
    }

    private TaskSqlConfig config(String loopConfigJson) {
        TaskSqlConfig config = new TaskSqlConfig();
        config.setId(1L);
        config.setDatasourceId(1L);
        config.setSqlContent("SELECT * FROM t");
        config.setSqlName("月度数据");
        config.setExcelLoopEnabled(1);
        config.setExcelLoopConfig(loopConfigJson);
        return config;
    }

    @Test
    void shouldGenerateMonthSheetsFrom202505ToCurrent() throws Exception {
        String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String json = "{\"startExpr\":\"2025-05\",\"endExpr\":\"" + currentMonth + "\",\"unit\":\"MONTH\",\"step\":1,\"sheetNameExpr\":\"${loopValue:yyyy年MM月}\"}";
        ExcelLoopHelper helper = new ExcelLoopHelper(stubExecutor());
        List<ExcelLoopHelper.LoopIterationResult> results = helper.expandLoop(config(json), Collections.emptyMap());

        assertFalse(results.isEmpty());
        assertEquals("2025年05月", results.get(0).sheetName());
        assertEquals("2025年05月", results.get(0).data().get(0).get("_sheet_name"));
        assertEquals(currentMonth, results.get(results.size() - 1).data().get(0).get("month").toString());
    }

    @Test
    void shouldRespectCustomSpelCondition() throws Exception {
        String json = "{\"startExpr\":\"2025-01\",\"endExpr\":\"2025-12\",\"unit\":\"MONTH\",\"step\":1,\"sheetNameExpr\":\"${loopValue:yyyy年MM月}\",\"condition\":\"#loopIndex \u003c 3\"}";
        ExcelLoopHelper helper = new ExcelLoopHelper(stubExecutor());
        List<ExcelLoopHelper.LoopIterationResult> results = helper.expandLoop(config(json), Collections.emptyMap());

        assertEquals(3, results.size());
        assertEquals("2025年01月", results.get(0).sheetName());
        assertEquals("2025年03月", results.get(2).sheetName());
    }

    @Test
    void shouldSkipEmptySheetsWhenConfigured() throws Exception {
        SqlExecutor emptyExecutor = new SqlExecutor(null) {
            @Override
            public List<Map<String, Object>> executeQuery(Long datasourceId, String sql, Map<String, Object> params) {
                return Collections.emptyList();
            }
        };
        String json = "{\"startExpr\":\"2025-01\",\"endExpr\":\"2025-03\",\"unit\":\"MONTH\",\"step\":1,\"sheetNameExpr\":\"${loopValue:yyyy年MM月}\",\"skipEmptySheet\":true}";
        ExcelLoopHelper helper = new ExcelLoopHelper(emptyExecutor);
        List<ExcelLoopHelper.LoopIterationResult> results = helper.expandLoop(config(json), Collections.emptyMap());

        assertTrue(results.isEmpty());
    }
}
