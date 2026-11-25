package com.coliving.platform.contract.mail.sender;

import com.coliving.platform.contract.mail.ContractMailProperties;
import com.coliving.platform.contract.mail.MailContent;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Gmail SMTP 발송. 접속 정보는 spring.mail.* (application.yaml, 비밀번호는 환경변수)
 *
 * @author 한성현
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class SmtpMailDelivery implements MailDelivery {

    private final JavaMailSender mailSender;
    private final ContractMailProperties props;

    public SmtpMailDelivery(JavaMailSender mailSender, ContractMailProperties props) {
        if (props.from() == null || props.from().isBlank()) {
            throw new IllegalStateException("app.mail.enabled=true이면 app.mail.from(보내는 Gmail 주소)이 필요합니다");
        }
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public void send(String to, MailContent content) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(props.from(), props.fromName());
        helper.setTo(to);
        helper.setSubject(content.subject());
        helper.setText(content.html(), true);
        mailSender.send(message);
    }
}
