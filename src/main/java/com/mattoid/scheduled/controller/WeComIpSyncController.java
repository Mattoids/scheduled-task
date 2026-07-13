package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.WeComIpSyncLog;
import com.mattoid.scheduled.service.wecom.WeComIpSyncLogService;
import com.mattoid.scheduled.service.wecom.WeComIpSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 企业微信可信 IP 同步 API。
 */
@Slf4j
@RestController
@RequestMapping("/api/wecom-ip-sync")
public class WeComIpSyncController {

    private final WeComIpSyncService weComIpSyncService;
    private final WeComIpSyncLogService weComIpSyncLogService;

    public WeComIpSyncController(WeComIpSyncService weComIpSyncService,
                                 WeComIpSyncLogService weComIpSyncLogService) {
        this.weComIpSyncService = weComIpSyncService;
        this.weComIpSyncLogService = weComIpSyncLogService;
    }

    /**
     * 生成企业微信管理后台登录二维码。
     */
    @PreAuthorize("hasAuthority('notificationConfig:view')")
    @PostMapping("/qr-code")
    public Result<Map<String, String>> generateQrCode() {
        return Result.ok(weComIpSyncService.generateLoginQrCode());
    }

    /**
     * 轮询二维码登录状态。
     */
    @PreAuthorize("hasAuthority('notificationConfig:view')")
    @GetMapping("/login-status/{sessionId}")
    public Result<Map<String, Object>> checkLoginStatus(@PathVariable String sessionId) {
        return Result.ok(weComIpSyncService.checkLoginStatus(sessionId));
    }

    /**
     * 手动触发指定配置的 IP 同步。
     */
    @PreAuthorize("hasAuthority('notificationConfig:view')")
    @PostMapping("/trigger/{configId}")
    public Result<Map<String, Object>> triggerSync(@PathVariable Long configId) {
        return Result.ok(weComIpSyncService.syncIpWhitelist(configId));
    }

    /**
     * 检测 Cookie 是否有效。
     * 传 configId 时校验已保存的配置；否则使用表单中的 agentId + adminCookie（保存前检测）。
     */
    @PreAuthorize("hasAuthority('notificationConfig:view')")
    @PostMapping("/check-cookie")
    public Result<Map<String, Object>> checkCookie(@RequestBody Map<String, Object> body) {
        Long configId = body.get("configId") != null ? Long.valueOf(body.get("configId").toString()) : null;
        Integer agentId = body.get("agentId") != null ? Integer.valueOf(body.get("agentId").toString()) : null;
        String adminCookie = body.get("adminCookie") != null ? body.get("adminCookie").toString() : null;
        String appManageUrl = body.get("appManageUrl") != null ? body.get("appManageUrl").toString() : null;
        return Result.ok(weComIpSyncService.checkCookieValid(configId, agentId, adminCookie, appManageUrl));
    }

    /**
     * 分页查询 IP 同步日志，可按通知配置、同步状态过滤。
     */
    @PreAuthorize("hasAuthority('notificationConfig:view')")
    @GetMapping("/logs")
    public Result<PageResult<WeComIpSyncLog>> logs(PageQuery query,
                                                   @RequestParam(required = false) Long configId,
                                                   @RequestParam(required = false) String status) {
        Page<WeComIpSyncLog> page = weComIpSyncLogService.pageAll(
                query.getCurrent(), query.getSize(), configId, status);
        return Result.ok(PageUtil.convert(page));
    }
}
