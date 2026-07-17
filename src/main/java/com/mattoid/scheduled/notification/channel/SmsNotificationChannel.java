package com.mattoid.scheduled.notification.channel;

import com.mattoid.scheduled.notification.NotificationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 短信通知渠道占位实现。
 *
 * 当前项目暂未接入短信服务商，调用时记录警告并跳过发送。
 */
@Slf4j
@Component
public class SmsNotificationChannel implements com.mattoid.scheduled.notification.NotificationChannel {

    @Override
    public String channelType() {
        return "SMS";
    }

    @Override
    public void send(NotificationContext context) {
        log.warn("短信通知渠道尚未实现，规则 {} 已跳过", context.getRule().getId());
    }
}
