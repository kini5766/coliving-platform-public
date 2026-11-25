package com.coliving.platform.security.mail;

import com.coliving.platform.common.config.MailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpPasswordResetMailSenderTest {

    @Test
    void sendsFromTheConfiguredMailbox() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        MailProperties mailProperties = new MailProperties(
                true, "roomlink@gmail.com", "Roomlink", "http://localhost:5173", Duration.ofMinutes(30));
        SmtpPasswordResetMailSender sender = new SmtpPasswordResetMailSender(mailSender, mailProperties);

        sender.send("resident-a@test.com", "http://localhost:5173/password-reset?token=raw-token");

        verify(mailSender).send(message);
        assertThat(message.getFrom()[0].toString()).contains("roomlink@gmail.com");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("resident-a@test.com");
        assertThat(message.getSubject()).isEqualTo("룸링크 비밀번호 재설정");
    }
}
