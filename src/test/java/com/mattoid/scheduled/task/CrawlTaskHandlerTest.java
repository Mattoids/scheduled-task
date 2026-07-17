package com.mattoid.scheduled.task;

import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.service.ChartGenerationService;
import com.mattoid.scheduled.service.ExcelGenerationService;
import com.mattoid.scheduled.service.ReportAssembler;
import com.mattoid.scheduled.service.ReportTemplateService;
import com.mattoid.scheduled.service.TaskWebCrawlConfigService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrawlTaskHandlerTest {

    @Mock
    private TaskWebCrawlConfigService taskWebCrawlConfigService;
    @Mock
    private WebCrawlExecutor webCrawlExecutor;
    @Mock
    private ExcelGenerationService excelGenerationService;
    @Mock
    private ChartGenerationService chartGenerationService;
    @Mock
    private TemplateProcessorFactory templateProcessorFactory;
    @Mock
    private ReportAssembler reportAssembler;

    @InjectMocks
    private CrawlTaskHandler handler;

    @Test
    void supports_crawlTask_returnsTrue() {
        TaskConfig task = new TaskConfig();
        task.setTaskType("CRAWL");
        assertTrue(handler.supports(task));
    }

    @Test
    void supports_nonCrawlTask_returnsFalse() {
        TaskConfig task = new TaskConfig();
        task.setTaskType("SQL");
        assertFalse(handler.supports(task));
    }

    @Test
    void handle_noCrawlConfigs_throwsIllegalArgumentException() {
        TaskConfig task = new TaskConfig();
        task.setTaskCode("TEST");
        task.setTaskType("CRAWL");
        when(taskWebCrawlConfigService.listByTaskCode("TEST")).thenReturn(Collections.emptyList());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(task, Collections.emptyMap()));
        assertEquals("任务未配置网页爬取模块", ex.getMessage());
    }

    @Test
    void handle_csvOutput_generatesReportFile() throws Exception {
        TaskConfig task = new TaskConfig();
        task.setId(1L);
        task.setTaskCode("TEST");
        task.setTaskType("CRAWL");

        TaskWebCrawlConfig crawlConfig = new TaskWebCrawlConfig();
        crawlConfig.setId(1L);
        crawlConfig.setCrawlCode("code1");
        crawlConfig.setCrawlName("name1");
        crawlConfig.setOutputFormat("CSV");

        when(taskWebCrawlConfigService.listByTaskCode("TEST")).thenReturn(List.of(crawlConfig));
        List<Map<String, Object>> data = List.of(Map.of("col", "value"));
        when(webCrawlExecutor.execute(crawlConfig, Collections.emptyMap()))
                .thenReturn(new WebCrawlResult("name1", "code1", data, Collections.emptyList(), 1, 0));

        File tempTemplate = File.createTempFile("template", ".csv");
        tempTemplate.deleteOnExit();
        when(reportAssembler.createTempCsvTemplate(data)).thenReturn(tempTemplate);
        when(reportAssembler.resolveExtension("CSV", null)).thenReturn("csv");
        File outputFile = new File("/tmp/reports/1/name1.csv");
        when(reportAssembler.buildOutputPath(task, crawlConfig, "csv")).thenReturn(outputFile.getAbsolutePath());

        TemplateProcessor processor = mock(TemplateProcessor.class);
        when(templateProcessorFactory.getProcessor("CSV")).thenReturn(processor);
        when(processor.process(tempTemplate, data, outputFile.getAbsolutePath())).thenReturn(outputFile);

        TaskExecutionResult result = handler.handle(task, Collections.emptyMap());

        assertEquals(1, result.getReportFiles().size());
        assertEquals(outputFile, result.getReportFiles().get(0));
    }

    @Test
    void handle_inlineOutput_addsInlineResult() throws Exception {
        TaskConfig task = new TaskConfig();
        task.setId(1L);
        task.setTaskCode("TEST");
        task.setTaskType("CRAWL");

        TaskWebCrawlConfig crawlConfig = new TaskWebCrawlConfig();
        crawlConfig.setId(1L);
        crawlConfig.setCrawlCode("code1");
        crawlConfig.setCrawlName("name1");
        crawlConfig.setOutputFormat("INLINE");

        when(taskWebCrawlConfigService.listByTaskCode("TEST")).thenReturn(List.of(crawlConfig));
        List<Map<String, Object>> data = List.of(Map.of("col", "value"));
        when(webCrawlExecutor.execute(crawlConfig, Collections.emptyMap()))
                .thenReturn(new WebCrawlResult("name1", "code1", data, Collections.emptyList(), 1, 0));

        TaskExecutionResult result = handler.handle(task, Collections.emptyMap());

        assertTrue(result.getReportFiles().isEmpty());
        assertEquals(1, result.getInlineResults().size());
        assertEquals("name1", result.getInlineResults().get(0).name());
    }
}
