package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.EmailConfig;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class EmailSenderServiceTest {

    @Test
    void sendEmail_delegatesMailSenderBuildingToNotificationConfigService() throws Exception {
        NotificationConfigService notificationConfigService = mock(NotificationConfigService.class);
        JavaMailSenderImpl mailSender = mock(JavaMailSenderImpl.class);
        MimeMessage message = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(message);
        when(notificationConfigService.buildJavaMailSender(any(EmailConfig.class))).thenReturn(mailSender);

        EmailSenderService service = new EmailSenderService(notificationConfigService);

        EmailConfig config = new EmailConfig();
        config.setSmtpHost("smtp.exmail.qq.com");
        config.setSmtpPort(587);
        config.setUsername("user@example.com");
        config.setPassword("pass");
        config.setFromAddress("from@example.com");
        config.setFromName("Sender");
        config.setAuth(1);
        config.setStarttls(1);

        service.sendEmail(config, List.of("to@example.com"), "subject", "body", null);

        verify(notificationConfigService).buildJavaMailSender(config);
        verify(mailSender).send(message);
    }
}
