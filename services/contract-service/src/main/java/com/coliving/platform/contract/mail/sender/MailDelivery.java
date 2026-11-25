package com.coliving.platform.contract.mail.sender;

import com.coliving.platform.contract.mail.MailContent;

/**
 * 메일을 실제로 내보내는 곳. app.mail.enabled에 따라 구현체가 하나만 뜬다.
 *   true  → SmtpMailDelivery (Gmail SMTP)
 *   false → LoggingMailDelivery (로그만, 기본값)
 *
 * @author 한성현
 */
public interface MailDelivery {
    void send(String to, MailContent content) throws Exception;
}
