package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.task.TaskExecutionResult;
import com.mattoid.scheduled.task.TaskHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskOrchestratorTest {

    @Mock
    private TaskConfigMapper taskConfigMapper;
    @Mock
    private TaskLogMapper taskLogMapper;
    @Mock
    private TaskHandler taskHandler;
    @Mock
    private ReportAssembler reportAssembler;
    @Mock
    private NotificationDispatcher notificationDispatcher;
    @Mock
    private TaskDependencyService taskDependencyService;

    private TaskOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new TaskOrchestrator(
                taskConfigMapper,
                taskLogMapper,
                List.of(taskHandler),
                reportAssembler,
                notificationDispatcher,
                taskDependencyService
        );
    }

    @Test
    void executeTask_taskNotFound_returnsNull() throws Exception {
        when(taskConfigMapper.selectById(1L)).thenReturn(null);

        TaskLog result = orchestrator.executeTask(1L, "AUTO");

        assertNull(result);
        verify(taskLogMapper, never()).insert(ArgumentMatchers.<TaskLog>any());
    }

    @Test
    void executeTask_concurrentRun_skipsExecution() throws Exception {
        TaskConfig task = new TaskConfig();
        task.setId(1L);
        task.setTaskType("SQL");
        when(taskConfigMapper.selectById(1L)).thenReturn(task);
        doAnswer(invocation -> {
            TaskLog log = invocation.getArgument(0);
            log.setId(100L);
            return null;
        }).when(taskLogMapper).insert(ArgumentMatchers.<TaskLog>any());
        when(taskLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        TaskLog result = orchestrator.executeTask(1L, "AUTO");

        assertNotNull(result);
        assertEquals("SKIPPED", result.getStatus());
        assertEquals("任务正在执行中，已跳过本次触发", result.getResultMessage());
        assertNotNull(result.getEndTime());
        verify(taskHandler, never()).handle(any(), anyMap());
    }

    @Test
    void executeTask_handlerSuccess_updatesLogAndDispatches() throws Exception {
        TaskConfig task = new TaskConfig();
        task.setId(1L);
        task.setTaskType("SQL");
        when(taskConfigMapper.selectById(1L)).thenReturn(task);
        doAnswer(invocation -> {
            TaskLog log = invocation.getArgument(0);
            log.setId(100L);
            return null;
        }).when(taskLogMapper).insert(ArgumentMatchers.<TaskLog>any());
        when(taskLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(taskHandler.supports(task)).thenReturn(true);
        TaskExecutionResult executionResult = new TaskExecutionResult();
        executionResult.addFile(new java.io.File("/tmp/report.csv"));
        when(taskHandler.handle(task, Collections.emptyMap())).thenReturn(executionResult);
        when(reportAssembler.assembleResultMessage(executionResult)).thenReturn("生成 1 个报表文件");
        when(reportAssembler.assembleFilePath(executionResult)).thenReturn("/tmp/report.csv");

        TaskLog result = orchestrator.executeTask(1L, "AUTO");

        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("生成 1 个报表文件", result.getResultMessage());
        assertEquals("/tmp/report.csv", result.getFilePath());
        verify(notificationDispatcher).dispatch(task, result, executionResult);
    }

    @Test
    void executeTask_handlerFailure_updatesLogAndDispatches() throws Exception {
        TaskConfig task = new TaskConfig();
        task.setId(1L);
        task.setTaskType("SQL");
        when(taskConfigMapper.selectById(1L)).thenReturn(task);
        doAnswer(invocation -> {
            TaskLog log = invocation.getArgument(0);
            log.setId(100L);
            return null;
        }).when(taskLogMapper).insert(ArgumentMatchers.<TaskLog>any());
        when(taskLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(taskHandler.supports(task)).thenReturn(true);
        when(taskHandler.handle(task, Collections.emptyMap()))
                .thenThrow(new IllegalArgumentException("SQL 错误"));

        TaskLog result = orchestrator.executeTask(1L, "AUTO");

        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertEquals("SQL 错误", result.getErrorMessage());
        ArgumentCaptor<TaskExecutionResult> resultCaptor = ArgumentCaptor.forClass(TaskExecutionResult.class);
        verify(notificationDispatcher).dispatch(eq(task), eq(result), resultCaptor.capture());
        assertTrue(resultCaptor.getValue().getReportFiles().isEmpty());
    }

    @Test
    void executeTaskWithDependencies_runsSeriallyAndStopsOnFailure() throws Exception {
        when(taskDependencyService.topologicalSort(1L)).thenReturn(List.of(1L, 2L, 3L));

        TaskConfig task1 = taskWithId(1L, "SQL");
        TaskConfig task2 = taskWithId(2L, "SQL");
        when(taskConfigMapper.selectById(1L)).thenReturn(task1);
        when(taskConfigMapper.selectById(2L)).thenReturn(task2);

        doAnswer(invocation -> {
            TaskLog log = invocation.getArgument(0);
            log.setId(log.getTaskId() + 100L);
            return null;
        }).when(taskLogMapper).insert(ArgumentMatchers.<TaskLog>any());
        when(taskLogMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(taskHandler.supports(any())).thenReturn(true);

        TaskExecutionResult successResult = new TaskExecutionResult();
        when(taskHandler.handle(eq(task1), anyMap())).thenReturn(successResult);
        when(taskHandler.handle(eq(task2), anyMap())).thenThrow(new RuntimeException("失败"));

        orchestrator.executeTaskWithDependencies(1L, "AUTO");

        verify(taskHandler, times(2)).handle(any(), anyMap());
    }

    private TaskConfig taskWithId(Long id, String type) {
        TaskConfig task = new TaskConfig();
        task.setId(id);
        task.setTaskType(type);
        return task;
    }
}
