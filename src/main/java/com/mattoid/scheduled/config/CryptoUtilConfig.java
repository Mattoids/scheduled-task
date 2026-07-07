package com.mattoid.scheduled.config;

import com.mattoid.scheduled.util.CryptoUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CryptoUtilConfig {

    @Value("${scheduled.task.aes.key:${SCHEDULED_TASK_AES_KEY:}}")
    private String aesKey;

    @PostConstruct
    public void init() {
        CryptoUtil.initialize(aesKey);
    }
}
