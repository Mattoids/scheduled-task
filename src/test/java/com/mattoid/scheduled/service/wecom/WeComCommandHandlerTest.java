package com.mattoid.scheduled.service.wecom;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.mattoid.scheduled.dto.CommandResult;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.service.AiAssistantService;
import com.mattoid.scheduled.service.ChartGenerationService;
import com.mattoid.scheduled.service.TaskConfigService;
import com.mattoid.scheduled.service.TaskExecutionService;
import com.mattoid.scheduled.service.TaskSqlConfigService;
import com.mattoid.scheduled.task.SqlExecutor;
import me.chanjar.weixin.cp.bean.message.WxCpXmlMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeComCommandHandlerTest {

    @Mock
    private TaskConfigService taskConfigService;

    @Mock
    private TaskExecutionService taskExecutionService;

    @Mock
    private TaskLogMapper taskLogMapper;

    @Mock
    private AiAssistantService aiAssistantService;

    @Mock
    private SqlExecutor sqlExecutor;

    @Mock
    private TaskSqlConfigService taskSqlConfigService;

    @Mock
    private ChartGenerationService chartGenerationService;

    @InjectMocks
    private WeComCommandHandler handler;

    @Test
    void handle_helpCommand_returnsHelpText() {
        WxCpXmlMessage message = new WxCpXmlMessage();
        message.setContent("帮助");

        CommandResult reply = handler.handle(message, 1L);

        assertTrue(reply.getText().contains("查询任务"));
        assertTrue(reply.getText().contains("运行"));
        assertTrue(reply.getText().contains("创建任务"));
    }

    @Test
    void handle_runCommand_triggersTask() {
        WxCpXmlMessage message = new WxCpXmlMessage();
        message.setContent("运行 1");

        TaskConfig task = new TaskConfig();
        task.setId(1L);
        task.setTaskName("测试任务");
        when(taskConfigService.getById(1L)).thenReturn(task);

        CommandResult reply = handler.handle(message, 1L);

        assertTrue(reply.getText().contains("已触发任务"));
        verify(taskExecutionService).executeTaskAsync(eq(1L), eq("MANUAL"), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handle_runCommandWithInvalidName_returnsNotFound() {
        WxCpXmlMessage message = new WxCpXmlMessage();
        message.setContent("运行 abc");

        LambdaQueryChainWrapper<TaskConfig> wrapper = org.mockito.Mockito.mock(LambdaQueryChainWrapper.class);
        when(taskConfigService.lambdaQuery()).thenReturn(wrapper);
        when(wrapper.like(any(), eq("abc"))).thenReturn(wrapper);
        when(wrapper.list()).thenReturn(Collections.emptyList());

        CommandResult reply = handler.handle(message, 1L);

        assertTrue(reply.getText().contains("未找到匹配的任务"));
    }

    @Test
    void handle_runCommandWithNonExistentTask_returnsError() {
        WxCpXmlMessage message = new WxCpXmlMessage();
        message.setContent("运行 999");
        when(taskConfigService.getById(999L)).thenReturn(null);

        CommandResult reply = handler.handle(message, 1L);

        assertTrue(reply.getText().contains("任务不存在"));
    }

    @Test
    void buildTaskResultSummary_containsStatusAndFiles() {
        TaskLog logEntity = new TaskLog();
        logEntity.setStatus("SUCCESS");
        logEntity.setResultMessage("生成 1 个报表文件");
        logEntity.setFilePath("/tmp/report.csv");

        TaskConfig task = new TaskConfig();
        task.setTaskName("测试任务");
        when(taskConfigService.getById(1L)).thenReturn(task);

        String summary = handler.buildTaskResultSummary(1L, logEntity);

        assertTrue(summary.contains("测试任务"));
        assertTrue(summary.contains("SUCCESS"));
        assertTrue(summary.contains("report.csv"));
    }
}
