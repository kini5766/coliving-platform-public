package com.coliving.platform.security.mail;

import com.coliving.platform.common.config.MailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpSignupVerificationMailSenderTest {

    @Test
    void sendsFromTheConfiguredMailbox() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        MailProperties mailProperties = new MailProperties(
                true, "roomlink@gmail.com", "Roomlink", "http://localhost:5173", Duration.ofMinutes(30));
        SmtpSignupVerificationMailSender sender = new SmtpSignupVerificationMailSender(mailSender, mailProperties);

        sender.send("person@gmail.com", "http://localhost:5173/signup/confirm?token=raw-token");

        verify(mailSender).send(message);
        assertThat(message.getFrom()[0].toString()).contains("roomlink@gmail.com");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("person@gmail.com");
        assertThat(message.getSubject()).isEqualTo("룸링크 회원가입 확인");
    }
}
