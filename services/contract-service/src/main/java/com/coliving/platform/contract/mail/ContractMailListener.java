package com.coliving.platform.contract.mail;

import com.coliving.platform.common.config.AsyncConfig;
import com.coliving.platform.contract.mail.sender.MailDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 메일 발송 (비동기, 커밋 후) — community-service NotificationPushListener와 같은 방식.
 *
 * - @TransactionalEventListener(기본 AFTER_COMMIT): 상태 변경이 커밋된 뒤에만 보낸다.
 *   승인 후 flush에서 @Version 충돌로 롤백되면 메일도 나가지 않는다.
 * - fallbackExecution = true: 트랜잭션 밖(D-day 스케줄러 등)에서 던진 이벤트도 바로 처리한다.
 * - @Async(mailTaskExecutor): SMTP가 느려도 API 응답·스케줄러를 붙잡지 않는다.
 * - 실패해도 상태 변경에는 영향이 없다 (로그만). 재시도가 필요해지면 아웃박스처럼 발송 기록 테이블을 둘 것.
 *
 * @author 한성현
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractMailListener {

    private final ContractMailComposer composer;
    private final MailDelivery delivery;

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @TransactionalEventListener(fallbackExecution = true)
    public void onMail(ContractMailEvent event) {
        ContractMailData d = event.data();
        String to = d.toEmail();
        if (to == null || to.isBlank()) {
            log.warn("메일 주소가 없어 발송 건너뜀 type={}, applicationId={}", event.type(), d.applicationId());
            return;
        }
        try {
            delivery.send(to, composer.compose(event));
            log.info("메일 발송 type={}, applicationId={}", event.type(), d.applicationId());
        } catch (Exception e) {
            log.warn("메일 발송 실패 type={}, applicationId={}", event.type(), d.applicationId(), e);
        }
    }
}
