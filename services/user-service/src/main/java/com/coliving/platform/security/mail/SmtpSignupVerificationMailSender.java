package com.coliving.platform.security.mail;

import com.coliving.platform.common.config.MailProperties;
import com.coliving.platform.security.dao.PendingSignupStore;
import com.coliving.platform.security.dao.SignupEmailVerificationTokenDAO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Gmail SMTP. 접속 정보는 spring.mail.* 이고 비밀번호는 MAIL_APP_PASSWORD 이다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "true")
public class SmtpSignupVerificationMailSender implements SignupVerificationMailSender {

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    /** 보내는 주소가 없으면 기동을 멈춘다. */
    public SmtpSignupVerificationMailSender(JavaMailSender mailSender, MailProperties mailProperties) {
        if (mailProperties.from() == null || mailProperties.from().isBlank()) {
            throw new IllegalStateException("app.mail.enabled=true이면 MAIL_USERNAME(보내는 Gmail 주소)이 필요합니다");
        }
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    /** HTML 가입 확인 메일을 보낸다. */
    @Override
    public void send(String to, String confirmUrl) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailProperties.from(), mailProperties.fromName());
            helper.setTo(to);
            helper.setSubject("룸링크 회원가입 확인");
            helper.setText(html(confirmUrl), true);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException ex) {
            throw new IllegalStateException("회원가입 확인 메일을 보내지 못했습니다.", ex);
        }
    }

    /** 버튼과 링크 만료, 대기 보관 기간을 담은 HTML. 주소는 이스케이프한다. */
    private String html(String confirmUrl) {
        String escapedUrl = HtmlUtils.htmlEscape(confirmUrl);
        long hours = SignupEmailVerificationTokenDAO.TTL.toHours();
        long days = PendingSignupStore.RETENTION.toDays();
        return """
                <!doctype html>
                <html lang="ko"><body style="margin:0;padding:24px;background:#f4f4f2;font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#222">
                <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;padding:32px">
                <h1 style="margin:0 0 16px;font-size:20px">회원가입 확인</h1>
                <p style="font-size:15px;line-height:1.6">룸링크 가입을 확인하려면 아래 버튼을 눌러 주세요. 이 링크는 %d시간 동안만 사용할 수 있습니다. 확인하지 않으면 가입 정보는 %d일 뒤에 삭제되고, 그 이메일로 다시 가입할 수 있습니다. 가입하지 않았다면 이 메일을 무시하세요.</p>
                <p style="margin:28px 0 0"><a href="%s" style="display:inline-block;padding:12px 22px;background:#2f5d50;color:#ffffff;text-decoration:none;border-radius:6px;font-weight:bold">가입 확인</a></p>
                <p style="margin:16px 0 0;font-size:12px;word-break:break-all">%s</p>
                <p style="margin:32px 0 0;font-size:12px;color:#888">본 메일은 발신 전용입니다.</p>
                </div></body></html>
                """.formatted(hours, days, escapedUrl, escapedUrl);
    }
}
