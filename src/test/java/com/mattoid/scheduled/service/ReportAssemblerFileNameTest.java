package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.entity.TaskSqlGroup;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class ReportAssemblerFileNameTest {

    private ReportAssembler createAssembler() {
        return new ReportAssembler("", null);
    }

    private String invokeBuildFileName(ReportAssembler assembler, TaskConfig task, TaskSqlConfig sqlConfig) throws Exception {
        Method method = ReportAssembler.class.getDeclaredMethod("buildFileName", TaskConfig.class, TaskSqlConfig.class);
        method.setAccessible(true);
        return (String) method.invoke(assembler, task, sqlConfig);
    }

    private TaskSqlConfig sqlConfigWithGroupPattern(String pattern) {
        TaskSqlGroup group = new TaskSqlGroup();
        group.setFileNamePattern(pattern);

        TaskSqlConfig sqlConfig = new TaskSqlConfig();
        sqlConfig.setTaskSqlGroup(group);
        return sqlConfig;
    }

    @Test
    void buildFileNameSupportsLastMonthPlaceholder() throws Exception {
        ReportAssembler assembler = createAssembler();
        TaskConfig task = new TaskConfig();
        TaskSqlConfig sqlConfig = sqlConfigWithGroupPattern("report_{lastMonth}.xlsx");

        String result = invokeBuildFileName(assembler, task, sqlConfig);
        YearMonth expected = YearMonth.now().minusMonths(1);
        assertEquals("report_" + expected.format(java.time.format.DateTimeFormatter.ofPattern("MM")) + ".xlsx", result);
    }

    @Test
    void buildFileNameSupportsLastMonthWithCustomPattern() throws Exception {
        ReportAssembler assembler = createAssembler();
        TaskConfig task = new TaskConfig();
        TaskSqlConfig sqlConfig = sqlConfigWithGroupPattern("report_{lastMonth:yyyy-MM}.xlsx");

        String result = invokeBuildFileName(assembler, task, sqlConfig);
        YearMonth expected = YearMonth.now().minusMonths(1);
        assertEquals("report_" + expected.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")) + ".xlsx", result);
    }

    @Test
    void buildFileNameSupportsNextMonthPlaceholder() throws Exception {
        ReportAssembler assembler = createAssembler();
        TaskConfig task = new TaskConfig();
        TaskSqlConfig sqlConfig = sqlConfigWithGroupPattern("report_{nextMonth}.xlsx");

        String result = invokeBuildFileName(assembler, task, sqlConfig);
        YearMonth expected = YearMonth.now().plusMonths(1);
        assertEquals("report_" + expected.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")) + ".xlsx", result);
    }

    @Test
    void buildFileNameStillSupportsStandardDatePattern() throws Exception {
        ReportAssembler assembler = createAssembler();
        TaskConfig task = new TaskConfig();
        TaskSqlConfig sqlConfig = sqlConfigWithGroupPattern("report_{yyyyMMdd}.xlsx");

        String result = invokeBuildFileName(assembler, task, sqlConfig);
        assertTrue(result.startsWith("report_"));
        assertTrue(result.endsWith(".xlsx"));
    }

    @Test
    void buildFileNameSupportsChinesePatternWithLastMonth() throws Exception {
        ReportAssembler assembler = createAssembler();
        TaskConfig task = new TaskConfig();
        TaskSqlConfig sqlConfig = sqlConfigWithGroupPattern("羽道同行{lastMonth:yyyy}年{lastMonth:M}月月报");

        String result = invokeBuildFileName(assembler, task, sqlConfig);
        YearMonth expected = YearMonth.now().minusMonths(1);
        String expectedYear = expected.format(java.time.format.DateTimeFormatter.ofPattern("yyyy"));
        String expectedMonth = expected.format(java.time.format.DateTimeFormatter.ofPattern("M"));
        assertEquals("羽道同行" + expectedYear + "年" + expectedMonth + "月月报", result);
    }

    @Test
    void buildFileNameFallsBackToDefaultPattern() throws Exception {
        ReportAssembler assembler = createAssembler();
        TaskConfig task = new TaskConfig();
        TaskSqlConfig sqlConfig = new TaskSqlConfig();

        String result = invokeBuildFileName(assembler, task, sqlConfig);
        assertTrue(result.startsWith("report_"));
    }
}
