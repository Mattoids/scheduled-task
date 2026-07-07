package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.NotificationRule;
import com.mattoid.scheduled.mapper.NotificationRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRuleServiceTest {

    @Mock
    private NotificationRuleMapper notificationRuleMapper;

    private NotificationRuleService notificationRuleService;

    @BeforeEach
    void setUp() {
        notificationRuleService = new NotificationRuleService();
        ReflectionTestUtils.setField(notificationRuleService, "baseMapper", notificationRuleMapper);
    }

    @Test
    void findEnabledByEventTypeAndTaskShouldFilterByEventTypeAndEnabledAndTask() {
        NotificationRule rule = new NotificationRule();
        rule.setId(1L);
        rule.setEventType("TASK_COMPLETED");
        rule.setChannel("EMAIL");
        rule.setEnabled(1);

        when(notificationRuleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(rule));

        List<NotificationRule> result = notificationRuleService.findEnabledByEventTypeAndTask("TASK_COMPLETED", "TEST_TASK");

        assertEquals(1, result.size());
        assertEquals("EMAIL", result.get(0).getChannel());

        ArgumentCaptor<LambdaQueryWrapper> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(notificationRuleMapper).selectList(captor.capture());
    }
}
