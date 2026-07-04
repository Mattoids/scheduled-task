package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.ai.AiChatResponse;
import com.mattoid.scheduled.ai.AiClient;
import com.mattoid.scheduled.ai.AiClientFactory;
import com.mattoid.scheduled.entity.AiConfig;
import com.mattoid.scheduled.mapper.AiConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class AiConfigService extends ServiceImpl<AiConfigMapper, AiConfig> {

    private final AiClientFactory aiClientFactory;

    public AiConfigService(AiClientFactory aiClientFactory) {
        this.aiClientFactory = aiClientFactory;
    }

    public AiConfig getDefaultConfig() {
        return lambdaQuery()
                .eq(AiConfig::getStatus, 1)
                .eq(AiConfig::getIsDefault, 1)
                .one();
    }

    /**
     * 获取实际使用的 AI 配置。若指定了 ID 且存在/启用则优先使用，否则回退到默认配置。
     */
    public AiConfig getEffectiveConfig(Long aiConfigId) {
        if (aiConfigId != null) {
            AiConfig config = getById(aiConfigId);
            if (config != null && config.getStatus() != null && config.getStatus() == 1) {
                return config;
            }
            log.warn("指定的 AI 配置不存在或已禁用: {}, 尝试使用默认配置", aiConfigId);
        }
        return getDefaultConfig();
    }

    public AiChatResponse testConfig(Long id) {
        AiConfig config = getById(id);
        if (config == null) {
            return AiChatResponse.error("AI 配置不存在");
        }
        return testConfig(config);
    }

    public AiChatResponse testConfig(AiConfig config) {
        AiClient client = aiClientFactory.createClient(config);
        return client.chat("你好，请简要回复 hello");
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateConfig(AiConfig config) {
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            // 清除其他默认配置
            lambdaUpdate()
                    .set(AiConfig::getIsDefault, 0)
                    .ne(config.getId() != null, AiConfig::getId, config.getId())
                    .update();
        }
        if (!StringUtils.hasText(config.getApiKey()) && config.getId() != null) {
            // 编辑时未填写 key，保留原 key
            AiConfig old = getById(config.getId());
            if (old != null) {
                config.setApiKey(old.getApiKey());
            }
        }
        return saveOrUpdate(config);
    }
}
