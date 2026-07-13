package com.mattoid.scheduled.service.wecom;

import com.mattoid.scheduled.entity.WeComAdminAccount;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信管理账户 Cookie 保活服务。
 * <p>每个启用保活的账户注册一个一次性延迟任务，随机 1~10 分钟（任意秒）后访问一次
 * 企业微信管理后台页面，执行完后再次随机调度，形成不规则的保活节奏，避免 Cookie 过期。</p>
 */
@Slf4j
@Service
public class WeComAdminAccountKeepAliveService implements ApplicationRunner {

    /** 保活访问的页面：管理后台 frame 页，轻量且能触发登录态校验 */
    private static final String KEEP_ALIVE_URL = "https://work.weixin.qq.com/wework_admin/frame";

    /** 随机延迟下界（秒） */
    private static final int MIN_DELAY_SECONDS = 60;
    /** 随机延迟上界（秒） */
    private static final int MAX_DELAY_SECONDS = 600;

    private static final String RESULT_OK = "保活成功";
    private static final String RESULT_COOKIE_INVALID = "保活失败：Cookie 已失效（被重定向到登录页）";
    private static final String RESULT_COOKIE_MISSING = "保活失败：Cookie 未配置";
    private static final String RESULT_ACCOUNT_GONE = "保活失败：账户不存在或已停用";

    private final WeComAdminAccountService weComAdminAccountService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "wecom-admin-keep-alive");
        t.setDaemon(true);
        return t;
    });

    /** accountId -> 当前已调度任务 */
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public WeComAdminAccountKeepAliveService(WeComAdminAccountService weComAdminAccountService) {
        this.weComAdminAccountService = weComAdminAccountService;
    }

    /** 应用启动后为所有启用保活的账户注册随机调度。 */
    @Override
    public void run(ApplicationArguments args) {
        List<WeComAdminAccount> accounts = weComAdminAccountService.listKeepAliveAccounts();
        for (WeComAdminAccount account : accounts) {
            schedule(account.getId());
        }
        log.info("企业微信管理账户 Cookie 保活调度初始化完成，共 {} 个账户", accounts.size());
    }

    /** 为指定账户注册下一次随机保活（账户启用且开启保活才生效）。 */
    public void schedule(Long accountId) {
        cancel(accountId);
        WeComAdminAccount account = weComAdminAccountService.getById(accountId);
        if (account == null || account.getStatus() == null || account.getStatus() != 1
                || !Boolean.TRUE.equals(account.getKeepAliveEnabled())) {
            return;
        }
        int delay = ThreadLocalRandom.current().nextInt(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS + 1);
        ScheduledFuture<?> future = scheduler.schedule(() -> runKeepAlive(accountId),
                delay, TimeUnit.SECONDS);
        scheduledTasks.put(accountId, future);
        log.debug("企业微信管理账户 {} 下一次保活将在 {} 秒后执行", accountId, delay);
    }

    /** 重新调度（账户信息变更后调用）。 */
    public void reschedule(Long accountId) {
        schedule(accountId);
    }

    /** 取消指定账户的保活调度。 */
    public void cancel(Long accountId) {
        ScheduledFuture<?> future = scheduledTasks.remove(accountId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /** 立即执行一次保活访问，返回执行结果。 */
    public Map<String, Object> keepAliveNow(Long accountId) {
        Map<String, Object> result = new HashMap<>();
        boolean ok = doKeepAlive(accountId);
        WeComAdminAccount account = weComAdminAccountService.getById(accountId);
        result.put("success", ok);
        result.put("message", account != null ? account.getLastKeepAliveResult() : RESULT_ACCOUNT_GONE);
        result.put("lastKeepAliveTime", account != null ? account.getLastKeepAliveTime() : null);
        return result;
    }

    private void runKeepAlive(Long accountId) {
        try {
            doKeepAlive(accountId);
        } catch (Exception e) {
            log.warn("企业微信管理账户 {} 保活执行异常: {}", accountId, e.getMessage());
        } finally {
            // 执行完后重新随机调度下一次
            schedule(accountId);
        }
    }

    /**
     * 执行一次保活访问：带 Cookie 请求管理后台页面，若被重定向到登录页则判定 Cookie 失效。
     *
     * @return true 表示 Cookie 仍有效
     */
    private boolean doKeepAlive(Long accountId) {
        WeComAdminAccount account = weComAdminAccountService.getById(accountId);
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            cancel(accountId);
            return false;
        }
        String cookie = weComAdminAccountService.getDecryptedCookie(accountId);
        if (!StringUtils.hasText(cookie)) {
            weComAdminAccountService.updateKeepAliveResult(accountId, RESULT_COOKIE_MISSING);
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(KEEP_ALIVE_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Cookie", cookie)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean invalid = isLoginRedirect(response);
            if (invalid) {
                weComAdminAccountService.updateKeepAliveResult(accountId, RESULT_COOKIE_INVALID);
                log.warn("企业微信管理账户 {}（{}）Cookie 已失效，请及时更新 Cookie", accountId, account.getAccountName());
                return false;
            }
            weComAdminAccountService.updateKeepAliveResult(accountId,
                    RESULT_OK + "（HTTP " + response.statusCode() + "）");
            return true;
        } catch (Exception e) {
            weComAdminAccountService.updateKeepAliveResult(accountId, "保活失败：" + e.getMessage());
            log.warn("企业微信管理账户 {} 保活请求失败: {}", accountId, e.getMessage());
            return false;
        }
    }

    /** 响应是否表明被引导到登录页（Cookie 失效特征）。 */
    private boolean isLoginRedirect(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
            String location = response.headers().firstValue("Location").orElse("");
            return location.contains("loginpage") || location.contains("login");
        }
        String body = response.body();
        return body != null && (body.contains("qrcode_login") || body.contains("loginpage_wx"));
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
