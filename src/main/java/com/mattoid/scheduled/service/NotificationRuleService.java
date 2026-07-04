package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.NotificationRule;
import com.mattoid.scheduled.mapper.NotificationRuleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationRuleService extends ServiceImpl<NotificationRuleMapper, NotificationRule> {

    public List<NotificationRule> findEnabledByEventTypeAndTask(String eventType, Long taskId) {
        LambdaQueryWrapper<NotificationRule> wrapper = new LambdaQueryWrapper<NotificationRule>()
                .eq(NotificationRule::getEventType, eventType)
                .eq(NotificationRule::getEnabled, 1)
                .and(w -> w.isNull(NotificationRule::getTaskId).or().eq(NotificationRule::getTaskId, taskId));
        return baseMapper.selectList(wrapper);
    }
}
