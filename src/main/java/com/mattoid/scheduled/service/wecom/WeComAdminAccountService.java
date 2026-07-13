package com.mattoid.scheduled.service.wecom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.WeComAdminAccount;
import com.mattoid.scheduled.mapper.WeComAdminAccountMapper;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业微信管理账户服务。
 * <p>Cookie 统一 AES 加密存储（ENC 前缀）；列表返回时下脱敏处理，不下发 Cookie 原文。</p>
 */
@Slf4j
@Service
public class WeComAdminAccountService extends ServiceImpl<WeComAdminAccountMapper, WeComAdminAccount> {

    /** 分页查询账户，可按名称模糊过滤；返回结果不含 Cookie 原文，仅标记是否已配置。 */
    public Page<WeComAdminAccount> pageAccounts(long current, long size, String keyword) {
        LambdaQueryWrapper<WeComAdminAccount> wrapper = new LambdaQueryWrapper<WeComAdminAccount>()
                .like(StringUtils.hasText(keyword), WeComAdminAccount::getAccountName, keyword)
                .orderByDesc(WeComAdminAccount::getCreateTime);
        Page<WeComAdminAccount> page = page(new Page<>(current, size), wrapper);
        maskCookie(page.getRecords());
        return page;
    }

    /** 查询全部启用且开启保活的账户（保活调度器启动时使用）。 */
    public List<WeComAdminAccount> listKeepAliveAccounts() {
        return lambdaQuery()
                .eq(WeComAdminAccount::getStatus, 1)
                .eq(WeComAdminAccount::getKeepAliveEnabled, true)
                .list();
    }

    /** 新建账户：Cookie 加密后存储。 */
    public WeComAdminAccount createAccount(String accountName, String adminCookie) {
        if (!StringUtils.hasText(accountName)) {
            throw new IllegalArgumentException("账户名称不能为空");
        }
        if (!StringUtils.hasText(adminCookie)) {
            throw new IllegalArgumentException("Cookie 不能为空，请先扫码登录");
        }
        WeComAdminAccount account = new WeComAdminAccount();
        account.setAccountName(accountName.trim());
        account.setAdminCookie(CryptoUtil.encrypt(adminCookie.trim()));
        account.setStatus(1);
        account.setKeepAliveEnabled(true);
        save(account);
        return account;
    }

    /** 更新账户；adminCookie 为空时保留原 Cookie，非空时加密覆盖。 */
    public WeComAdminAccount updateAccount(Long id, String accountName, Integer status,
                                           Boolean keepAliveEnabled, String adminCookie) {
        WeComAdminAccount account = getById(id);
        if (account == null) {
            throw new IllegalArgumentException("账户不存在: " + id);
        }
        if (StringUtils.hasText(accountName)) {
            account.setAccountName(accountName.trim());
        }
        if (status != null) {
            account.setStatus(status);
        }
        if (keepAliveEnabled != null) {
            account.setKeepAliveEnabled(keepAliveEnabled);
        }
        if (StringUtils.hasText(adminCookie)) {
            account.setAdminCookie(CryptoUtil.encrypt(adminCookie.trim()));
        }
        updateById(account);
        return account;
    }

    /** 获取解密后的 Cookie 原文（保活、免登录跳转内部使用）。 */
    public String getDecryptedCookie(Long id) {
        WeComAdminAccount account = getById(id);
        if (account == null || !StringUtils.hasText(account.getAdminCookie())) {
            return null;
        }
        return CryptoUtil.decryptIfNeeded(account.getAdminCookie());
    }

    /** 更新最近一次保活结果。 */
    public void updateKeepAliveResult(Long id, String result) {
        WeComAdminAccount update = new WeComAdminAccount();
        update.setId(id);
        update.setLastKeepAliveTime(LocalDateTime.now());
        update.setLastKeepAliveResult(result != null && result.length() > 500
                ? result.substring(0, 500) : result);
        updateById(update);
    }

    private void maskCookie(List<WeComAdminAccount> records) {
        for (WeComAdminAccount account : records) {
            account.setCookieConfigured(StringUtils.hasText(account.getAdminCookie()));
            account.setAdminCookie(null);
        }
    }
}
