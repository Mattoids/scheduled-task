package com.mattoid.scheduled.service.wecom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 企业微信可信 IP 自动同步调度器。
 * 每分钟检查一次，根据各配置的同步间隔决定是否执行同步。
 */
@Slf4j
@Component
@EnableScheduling
public class WeComIpSyncScheduler {

    private final WeComIpSyncService weComIpSyncService;

    public WeComIpSyncScheduler(WeComIpSyncService weComIpSyncService) {
        this.weComIpSyncService = weComIpSyncService;
    }

    /**
     * 每分钟执行一次，检查所有启用自动同步的配置。
     * 具体间隔由 WeComIpSyncService 内部的 lastSyncTimes 控制。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void syncTrustedIps() {
        try {
            weComIpSyncService.syncAllEnabledConfigs();
        } catch (Exception e) {
            log.error("定时同步可信 IP 任务执行异常", e);
        }
    }

    /**
     * 每 2 分钟清理过期的二维码登录会话。
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void cleanupExpiredSessions() {
        try {
            weComIpSyncService.cleanupExpiredSessions();
        } catch (Exception e) {
            log.error("清理过期二维码会话异常", e);
        }
    }
}
