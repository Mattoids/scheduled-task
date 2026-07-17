package com.mattoid.scheduled.notification;

import org.springframework.util.StringUtils;

/**
 * 通知渠道 SPI。每个具体渠道实现该接口并通过 Spring Bean 注册，
 * 由 {@link com.mattoid.scheduled.event.NotificationEventListener} 统一路由与重试。
 */
public interface NotificationChannel {

    /**
     * 渠道类型标识，对应 NotificationRule.channel。
     */
    String channelType();

    /**
     * 发送通知。
     *
     * @param context 统一上下文，包含事件、规则及已解析的配置
     * @throws Exception 发送失败时抛出，由上层进行重试
     */
    void send(NotificationContext context) throws Exception;

    /**
     * 解析通知接收者，用于记录通知日志。
     * 默认实现返回企业微信的 wecomToUser 或 @all，各渠道可覆盖。
     */
    default String resolveRecipient(NotificationContext context) {
        String toUser = context.getRule() != null ? context.getRule().getWecomToUser() : null;
        return StringUtils.hasText(toUser) ? toUser : "@all";
    }
}
