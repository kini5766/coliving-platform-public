package com.coliving.platform.common.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * app.mail.enabled 는 환경변수 MAIL_ENABLED 를 본다.
 * 발송 주소와 SMTP 비밀번호는 MAIL_USERNAME, MAIL_APP_PASSWORD 이다.
 *
 * @author 조휘재
 * 작성일: 2026-11-11
 */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String from,
        @DefaultValue("Roomlink") String fromName,
        @DefaultValue("http://localhost:5173") String linkBaseUrl,
        @DefaultValue("30m") Duration resetTokenTtl
) {
    /** 빈 발신 이름·주소·링크·만료를 기본값으로 맞추고, 링크 끝의 / 를 뺀다. */
    public MailProperties {
        if (from == null) {
            from = "";
        }
        if (fromName == null || fromName.isBlank()) {
            fromName = "Roomlink";
        }
        if (linkBaseUrl == null || linkBaseUrl.isBlank()) {
            linkBaseUrl = "http://localhost:5173";
        }
        if (linkBaseUrl.endsWith("/")) {
            linkBaseUrl = linkBaseUrl.substring(0, linkBaseUrl.length() - 1);
        }
        if (resetTokenTtl == null || resetTokenTtl.isZero() || resetTokenTtl.isNegative()) {
            resetTokenTtl = Duration.ofMinutes(30);
        }
    }

    /** 재설정 링크. 토큰은 user-web 쿼리로만 붙인다. */
    public String resetUrl(String token) {
        return linkBaseUrl + "/password-reset?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /** 가입 확인 링크. 토큰은 user-web 쿼리로만 붙인다. */
    public String signupConfirmUrl(String token) {
        return linkBaseUrl + "/signup/confirm?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
