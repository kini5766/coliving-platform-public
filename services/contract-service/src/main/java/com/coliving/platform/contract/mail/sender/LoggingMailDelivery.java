package com.coliving.platform.contract.mail.sender;

import com.coliving.platform.contract.mail.MailContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발용: 메일을 보내지 않고 로그만 남긴다 (app.mail.enabled가 없거나 false)
 *
 * @author 한성현
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingMailDelivery implements MailDelivery {

    @Override
    public void send(String to, MailContent content) {
        log.info("[메일 미발송: app.mail.enabled=false] to={}, subject={}", to, content.subject());
    }
}
