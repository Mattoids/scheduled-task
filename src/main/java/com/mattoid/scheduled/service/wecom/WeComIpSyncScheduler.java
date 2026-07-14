package com.mattoid.scheduled.service.wecom;

import com.mattoid.scheduled.service.BrowserCapabilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 企业微信可信 IP 自动同步调度器。
 * 每分钟检查一次，根据各配置的同步间隔决定是否执行同步。
 */
@Slf4j
@Component
@EnableScheduling
public class WeComIpSyncScheduler {

    private final WeComIpSyncService weComIpSyncService;
    private final BrowserCapabilityService browserCapabilityService;
    private final AtomicBoolean loggedUnavailable = new AtomicBoolean(false);

    public WeComIpSyncScheduler(WeComIpSyncService weComIpSyncService,
                                BrowserCapabilityService browserCapabilityService) {
        this.weComIpSyncService = weComIpSyncService;
        this.browserCapabilityService = browserCapabilityService;
    }

    /**
     * 每分钟执行一次，检查所有启用自动同步的配置。
     * 具体间隔由 WeComIpSyncService 内部的 lastSyncTimes 控制。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void syncTrustedIps() {
        if (!checkChromiumAvailable()) {
            return;
        }
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
        if (!checkChromiumAvailable()) {
            return;
        }
        try {
            weComIpSyncService.cleanupExpiredSessions();
        } catch (Exception e) {
            log.error("清理过期二维码会话异常", e);
        }
    }

    private boolean checkChromiumAvailable() {
        boolean available = browserCapabilityService.isChromiumAvailable();
        if (!available) {
            if (loggedUnavailable.compareAndSet(false, true)) {
                log.warn("Chromium 内核未安装或不可用，跳过企业微信相关定时任务");
            }
            return false;
        }
        loggedUnavailable.set(false);
        return true;
    }
}
