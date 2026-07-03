package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.entity.WeComBotConfig;
import com.mattoid.scheduled.mapper.WeComBotConfigMapper;
import com.mattoid.scheduled.service.wecom.WeComBotClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WeComBotConfigService extends ServiceImpl<WeComBotConfigMapper, WeComBotConfig> {

    private final WeComBotClient weComBotClient;

    public WeComBotConfigService(WeComBotClient weComBotClient) {
        this.weComBotClient = weComBotClient;
    }

    public TestConnectionResult testConnection(WeComBotConfig config) {
        if (config == null) {
            return TestConnectionResult.fail("配置不能为空");
        }
        try {
            weComBotClient.sendText(config.getWebhookKey(), "连接测试");
            return TestConnectionResult.ok();
        } catch (Exception e) {
            log.error("测试企业微信群机器人配置失败: {}", config.getConfigName(), e);
            return TestConnectionResult.fail(e.getMessage());
        }
    }
}
