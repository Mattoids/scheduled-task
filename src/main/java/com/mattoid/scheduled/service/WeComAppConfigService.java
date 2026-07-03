package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.entity.WeComAppConfig;
import com.mattoid.scheduled.mapper.WeComAppConfigMapper;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class WeComAppConfigService extends ServiceImpl<WeComAppConfigMapper, WeComAppConfig> {

    private final WeComAppManager weComAppManager;

    public WeComAppConfigService(WeComAppManager weComAppManager) {
        this.weComAppManager = weComAppManager;
    }

    @Override
    public boolean saveOrUpdate(WeComAppConfig entity) {
        if (StringUtils.hasText(entity.getSecret()) && !entity.getSecret().startsWith("ENC(")) {
            entity.setSecret(CryptoUtil.encrypt(entity.getSecret()));
        }
        boolean result = super.saveOrUpdate(entity);
        if (result && entity.getId() != null) {
            weComAppManager.invalidateCache(entity.getId());
        }
        return result;
    }

    public TestConnectionResult testConnection(WeComAppConfig config) {
        if (config == null) {
            return TestConnectionResult.fail("配置不能为空");
        }
        if (config.getId() != null) {
            weComAppManager.invalidateCache(config.getId());
        }
        try {
            WeComAppConfig temp = new WeComAppConfig();
            temp.setCorpId(config.getCorpId());
            temp.setAgentId(config.getAgentId());
            temp.setSecret(CryptoUtil.decryptIfNeeded(config.getSecret()));
            temp.setToken(config.getToken());
            temp.setAesKey(config.getAesKey());
            temp.setStatus(1);
            weComAppManager.buildTempService(temp).getAccessToken();
            return TestConnectionResult.ok();
        } catch (Exception e) {
            log.error("测试企业微信应用配置失败: {}", config.getConfigName(), e);
            return TestConnectionResult.fail(e.getMessage());
        }
    }
}
