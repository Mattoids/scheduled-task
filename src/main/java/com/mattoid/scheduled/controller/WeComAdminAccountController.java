package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.WeComAdminAccount;
import com.mattoid.scheduled.service.wecom.WeComAdminAccountKeepAliveService;
import com.mattoid.scheduled.service.wecom.WeComAdminAccountService;
import com.mattoid.scheduled.service.wecom.WeComAdminSsoService;
import com.mattoid.scheduled.service.wecom.WeComIpSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 企业应用管理 API：管理多个企业微信管理后台账户（Cookie）。
 */
@Slf4j
@RestController
@RequestMapping("/api/wecom-admin/accounts")
public class WeComAdminAccountController {

    private final WeComAdminAccountService weComAdminAccountService;
    private final WeComAdminAccountKeepAliveService keepAliveService;
    private final WeComIpSyncService weComIpSyncService;
    private final WeComAdminSsoService weComAdminSsoService;

    public WeComAdminAccountController(WeComAdminAccountService weComAdminAccountService,
                                       WeComAdminAccountKeepAliveService keepAliveService,
                                       WeComIpSyncService weComIpSyncService,
                                       WeComAdminSsoService weComAdminSsoService) {
        this.weComAdminAccountService = weComAdminAccountService;
        this.keepAliveService = keepAliveService;
        this.weComIpSyncService = weComIpSyncService;
        this.weComAdminSsoService = weComAdminSsoService;
    }

    /** 查询所有启用的账户（下拉选择用，不含 Cookie 原文）。 */
    @PreAuthorize("hasAuthority('notificationConfig:view')")
    @GetMapping("/enabled")
    public Result<java.util.List<WeComAdminAccount>> listEnabled() {
        java.util.List<WeComAdminAccount> accounts = weComAdminAccountService.lambdaQuery()
                .eq(WeComAdminAccount::getStatus, 1)
                .orderByAsc(WeComAdminAccount::getAccountName)
                .list();
        for (WeComAdminAccount account : accounts) {
            account.setCookieConfigured(StringUtils.hasText(account.getAdminCookie()));
            account.setAdminCookie(null);
        }
        return Result.ok(accounts);
    }

    /** 分页查询账户（不含 Cookie 原文）。 */
    @PreAuthorize("hasAuthority('wecomAdmin:view')")
    @GetMapping
    public Result<PageResult<WeComAdminAccount>> page(PageQuery query,
                                                      @RequestParam(required = false) String keyword) {
        Page<WeComAdminAccount> page = weComAdminAccountService.pageAccounts(
                query.getCurrent(), query.getSize(), keyword);
        return Result.ok(PageUtil.convert(page));
    }

    /** 新建账户（扫码获取 Cookie 后提交）。 */
    @PreAuthorize("hasAuthority('wecomAdmin:create')")
    @PostMapping
    public Result<WeComAdminAccount> create(@RequestBody Map<String, Object> body) {
        String accountName = body.get("accountName") != null ? body.get("accountName").toString() : null;
        String adminCookie = body.get("adminCookie") != null ? body.get("adminCookie").toString() : null;
        WeComAdminAccount account = weComAdminAccountService.createAccount(accountName, adminCookie);
        keepAliveService.schedule(account.getId());
        return Result.ok(account);
    }

    /** 更新账户（名称/状态/保活开关/Cookie）。 */
    @PreAuthorize("hasAuthority('wecomAdmin:edit')")
    @PutMapping("/{id}")
    public Result<WeComAdminAccount> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String accountName = body.get("accountName") != null ? body.get("accountName").toString() : null;
        Integer status = body.get("status") != null ? Integer.valueOf(body.get("status").toString()) : null;
        Boolean keepAliveEnabled = body.get("keepAliveEnabled") != null
                ? Boolean.valueOf(body.get("keepAliveEnabled").toString()) : null;
        String adminCookie = body.get("adminCookie") != null ? body.get("adminCookie").toString() : null;
        WeComAdminAccount account = weComAdminAccountService.updateAccount(
                id, accountName, status, keepAliveEnabled, adminCookie);
        keepAliveService.reschedule(id);
        return Result.ok(account);
    }

    /** 删除账户并取消保活任务。 */
    @PreAuthorize("hasAuthority('wecomAdmin:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        keepAliveService.cancel(id);
        weComAdminAccountService.removeById(id);
        return Result.ok();
    }

    /** 立即执行一次 Cookie 保活访问。 */
    @PreAuthorize("hasAuthority('wecomAdmin:edit')")
    @PostMapping("/{id}/keep-alive")
    public Result<Map<String, Object>> keepAliveNow(@PathVariable Long id) {
        return Result.ok(keepAliveService.keepAliveNow(id));
    }

    /**
     * 打开已登录的企业微信管理后台（在本机弹出有头浏览器窗口）。
     * 浏览器中已注入账户存储的 Cookie，用户可直接操作。
     */
    @PreAuthorize("hasAuthority('wecomAdmin:view')")
    @PostMapping("/{id}/open-admin")
    public Result<Void> openAdmin(@PathVariable Long id) {
        String cookie = weComAdminAccountService.getDecryptedCookie(id);
        if (!StringUtils.hasText(cookie)) {
            throw new IllegalArgumentException("该账户未配置 Cookie");
        }
        weComIpSyncService.openAdminBrowser(cookie);
        return Result.ok();
    }

    /**
     * 签发免登录跳转一次性 ticket。
     * 前端用 window.open 打开代理入口时无法携带 JWT，故先换取短期有效的一次性 ticket。
     */
    @PreAuthorize("hasAuthority('wecomAdmin:view')")
    @PostMapping("/{id}/sso-ticket")
    public Result<Map<String, String>> ssoTicket(@PathVariable Long id) {
        return Result.ok(weComAdminSsoService.issueTicket(id));
    }
}
