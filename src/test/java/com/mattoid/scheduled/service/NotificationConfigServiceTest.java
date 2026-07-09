package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.EmailConfig;
import com.mattoid.scheduled.service.notify.DingTalkClient;
import com.mattoid.scheduled.service.notify.FeishuClient;
import com.mattoid.scheduled.service.notify.SlackClient;
import com.mattoid.scheduled.service.notify.WebhookClient;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.service.wecom.WeComBotClient;
import com.mattoid.scheduled.service.wecom.WeComIntelligentBotClient;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class NotificationConfigServiceTest {

    private NotificationConfigService createService() {
        return new NotificationConfigService(
                mock(WeComAppManager.class),
                mock(WeComBotClient.class),
                mock(WeComIntelligentBotClient.class),
                mock(DingTalkClient.class),
                mock(FeishuClient.class),
                mock(SlackClient.class),
                mock(WebhookClient.class)
        );
    }

    @Test
    void buildJavaMailSender_withSslEnabled_configuresSslProperties() {
        NotificationConfigService service = createService();
        EmailConfig config = new EmailConfig();
        config.setSmtpHost("smtp.exmail.qq.com");
        config.setSmtpPort(465);
        config.setUsername("user@example.com");
        config.setPassword("pass");
        config.setAuth(1);
        config.setStarttls(1);
        config.setSsl(1);

        JavaMailSenderImpl sender = service.buildJavaMailSender(config);
        Properties props = sender.getJavaMailProperties();

        assertEquals("smtp.exmail.qq.com", sender.getHost());
        assertEquals(465, sender.getPort());
        assertEquals("true", props.getProperty("mail.smtp.auth"));
        assertEquals("false", props.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", props.getProperty("mail.smtp.ssl.enable"));
        assertEquals("true", props.getProperty("mail.smtp.ssl.required"));
        assertEquals("false", props.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertEquals("*", props.getProperty("mail.smtp.ssl.trust"));
        assertEquals("javax.net.ssl.SSLSocketFactory", props.getProperty("mail.smtp.socketFactory.class"));
        assertEquals("false", props.getProperty("mail.smtp.socketFactory.fallback"));
        assertEquals("465", props.getProperty("mail.smtp.socketFactory.port"));
    }

    @Test
    void buildJavaMailSender_withStarttlsOnly_configuresStarttls() {
        NotificationConfigService service = createService();
        EmailConfig config = new EmailConfig();
        config.setSmtpHost("smtp.example.com");
        config.setSmtpPort(587);
        config.setUsername("user@example.com");
        config.setPassword("pass");
        config.setAuth(1);
        config.setStarttls(1);
        config.setSsl(0);

        JavaMailSenderImpl sender = service.buildJavaMailSender(config);
        Properties props = sender.getJavaMailProperties();

        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"));
        assertNull(props.getProperty("mail.smtp.ssl.enable"));
        assertNull(props.getProperty("mail.smtp.socketFactory.class"));
    }
}
