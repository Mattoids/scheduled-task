package com.mattoid.scheduled.task;

import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.service.ChartGenerationService;
import com.mattoid.scheduled.service.ExcelGenerationService;
import com.mattoid.scheduled.service.ExcelLoopHelper;
import com.mattoid.scheduled.service.ReportAssembler;
import com.mattoid.scheduled.service.ReportTemplateService;
import com.mattoid.scheduled.service.TaskSqlConfigService;
import com.mattoid.scheduled.template.TemplateProcessor;
import com.mattoid.scheduled.template.TemplateProcessorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqlTaskHandlerTest {

    @Mock
    private SqlExecutor sqlExecutor;
    @Mock
    private ReportTemplateService reportTemplateService;
    @Mock
    private TaskSqlConfigService taskSqlConfigService;
    @Mock
    private ExcelLoopHelper excelLoopHelper;
    @Mock
    private ExcelGenerationService excelGenerationService;
    @Mock
    private ChartGenerationService chartGenerationService;
    @Mock
    private TemplateProcessorFactory templateProcessorFactory;
    @Mock
    private ReportAssembler reportAssembler;

    @InjectMocks
    private SqlTaskHandler handler;

    @Test
    void supports_sqlTask_returnsTrue() {
        TaskConfig task = new TaskConfig();
        task.setTaskType("SQL");
        assertTrue(handler.supports(task));
    }

    @Test
    void supports_nullTask_returnsFalse() {
        assertFalse(handler.supports(null));
    }

    @Test
    void supports_crawlTask_returnsFalse() {
        TaskConfig task = new TaskConfig();
        task.setTaskType("CRAWL");
        assertFalse(handler.supports(task));
    }

    @Test
    void handle_noSqlConfigs_throwsIllegalArgumentException() {
        TaskConfig task = new TaskConfig();
        task.setTaskCode("TEST");
        task.setTaskType("SQL");
        when(taskSqlConfigService.listByTaskCode("TEST")).thenReturn(Collections.emptyList());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(task, Collections.emptyMap()));
        assertEquals("任务未配置 SQL 模块", ex.getMessage());
    }

    @Test
    void handle_csvOutput_generatesReportFile() throws Exception {
        TaskConfig task = new TaskConfig();
        task.setId(1L);
        task.setTaskCode("TEST");
        task.setTaskType("SQL");

        TaskSqlConfig sqlConfig = new TaskSqlConfig();
        sqlConfig.setId(1L);
        sqlConfig.setSqlCode("code1");
        sqlConfig.setSqlName("name1");
        sqlConfig.setOutputFormat("CSV");
        sqlConfig.setDatasourceId(1L);
        sqlConfig.setSqlContent("SELECT 1");

        when(taskSqlConfigService.listByTaskCode("TEST")).thenReturn(List.of(sqlConfig));
        when(excelLoopHelper.isLoopEnabled(sqlConfig)).thenReturn(false);
        List<Map<String, Object>> data = List.of(Map.of("col", "value"));
        when(sqlExecutor.executeQuery(1L, "SELECT 1", Collections.emptyMap())).thenReturn(data);

        File tempTemplate = File.createTempFile("template", ".csv");
        tempTemplate.deleteOnExit();
        when(reportAssembler.createTempCsvTemplate(data)).thenReturn(tempTemplate);
        when(reportAssembler.resolveExtension("CSV", null)).thenReturn("csv");
        when(reportAssembler.buildOutputPath(task, sqlConfig, "csv")).thenReturn("/tmp/reports/1/name1.csv");

        File outputFile = new File("/tmp/reports/1/name1.csv");
        TemplateProcessor processor = mock(TemplateProcessor.class);
        when(templateProcessorFactory.getProcessor("CSV")).thenReturn(processor);
        when(processor.process(tempTemplate, data, outputFile.getAbsolutePath())).thenReturn(outputFile);

        TaskExecutionResult result = handler.handle(task, Collections.emptyMap());

        assertEquals(1, result.getReportFiles().size());
        assertEquals(outputFile, result.getReportFiles().get(0));
        assertTrue(result.getInlineResults().isEmpty());
    }

    @Test
    void handle_inlineOutput_addsInlineResult() throws Exception {
        TaskConfig task = new TaskConfig();
        task.setId(1L);
        task.setTaskCode("TEST");
        task.setTaskType("SQL");

        TaskSqlConfig sqlConfig = new TaskSqlConfig();
        sqlConfig.setId(1L);
        sqlConfig.setSqlCode("code1");
        sqlConfig.setSqlName("name1");
        sqlConfig.setOutputFormat("INLINE");
        sqlConfig.setDatasourceId(1L);
        sqlConfig.setSqlContent("SELECT 1");

        when(taskSqlConfigService.listByTaskCode("TEST")).thenReturn(List.of(sqlConfig));
        when(excelLoopHelper.isLoopEnabled(sqlConfig)).thenReturn(false);
        List<Map<String, Object>> data = List.of(Map.of("col", "value"));
        when(sqlExecutor.executeQuery(1L, "SELECT 1", Collections.emptyMap())).thenReturn(data);

        TaskExecutionResult result = handler.handle(task, Collections.emptyMap());

        assertTrue(result.getReportFiles().isEmpty());
        assertEquals(1, result.getInlineResults().size());
        assertEquals("name1", result.getInlineResults().get(0).name());
    }
}
