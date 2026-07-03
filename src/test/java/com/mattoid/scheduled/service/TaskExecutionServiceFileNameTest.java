package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class TaskExecutionServiceFileNameTest {

    @Test
    void buildFileNameSupportsLastMonthPlaceholder() throws Exception {
        TaskExecutionService service = new TaskExecutionService(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        Method method = TaskExecutionService.class.getDeclaredMethod("buildFileName", TaskConfig.class, TaskSqlConfig.class, boolean.class);
        method.setAccessible(true);

        TaskConfig task = new TaskConfig();
        task.setFileNamePattern("report_{lastMonth}.xlsx");

        String result = (String) method.invoke(service, task, null, false);
        YearMonth expected = YearMonth.now().minusMonths(1);
        assertEquals("report_" + expected.format(java.time.format.DateTimeFormatter.ofPattern("MM")) + ".xlsx", result);
    }

    @Test
    void buildFileNameSupportsLastMonthWithCustomPattern() throws Exception {
        TaskExecutionService service = new TaskExecutionService(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        Method method = TaskExecutionService.class.getDeclaredMethod("buildFileName", TaskConfig.class, TaskSqlConfig.class, boolean.class);
        method.setAccessible(true);

        TaskConfig task = new TaskConfig();
        task.setFileNamePattern("report_{lastMonth:yyyy-MM}.xlsx");

        String result = (String) method.invoke(service, task, null, false);
        YearMonth expected = YearMonth.now().minusMonths(1);
        assertEquals("report_" + expected.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")) + ".xlsx", result);
    }

    @Test
    void buildFileNameSupportsNextMonthPlaceholder() throws Exception {
        TaskExecutionService service = new TaskExecutionService(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        Method method = TaskExecutionService.class.getDeclaredMethod("buildFileName", TaskConfig.class, TaskSqlConfig.class, boolean.class);
        method.setAccessible(true);

        TaskConfig task = new TaskConfig();
        task.setFileNamePattern("report_{nextMonth}.xlsx");

        String result = (String) method.invoke(service, task, null, false);
        YearMonth expected = YearMonth.now().plusMonths(1);
        assertEquals("report_" + expected.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")) + ".xlsx", result);
    }

    @Test
    void buildFileNameStillSupportsStandardDatePattern() throws Exception {
        TaskExecutionService service = new TaskExecutionService(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        Method method = TaskExecutionService.class.getDeclaredMethod("buildFileName", TaskConfig.class, TaskSqlConfig.class, boolean.class);
        method.setAccessible(true);

        TaskConfig task = new TaskConfig();
        task.setFileNamePattern("report_{yyyyMMdd}.xlsx");

        String result = (String) method.invoke(service, task, null, false);
        assertTrue(result.startsWith("report_"));
        assertTrue(result.endsWith(".xlsx"));
    }

    @Test
    void buildFileNameSupportsChinesePatternWithLastMonth() throws Exception {
        TaskExecutionService service = new TaskExecutionService(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null
        );
        Method method = TaskExecutionService.class.getDeclaredMethod("buildFileName", TaskConfig.class, TaskSqlConfig.class, boolean.class);
        method.setAccessible(true);

        TaskConfig task = new TaskConfig();
        task.setFileNamePattern("羽道同行{lastMonth:yyyy}年{lastMonth:M}月月报");

        String result = (String) method.invoke(service, task, null, false);
        YearMonth expected = YearMonth.now().minusMonths(1);
        String expectedYear = expected.format(java.time.format.DateTimeFormatter.ofPattern("yyyy"));
        String expectedMonth = expected.format(java.time.format.DateTimeFormatter.ofPattern("M"));
        assertEquals("羽道同行" + expectedYear + "年" + expectedMonth + "月月报", result);
    }
}
