package com.coliving.platform.contract.mail;

/**
 * 메일 발송 요청. ApplicationEventPublisher로 던지면 ContractMailListener가 받는다.
 * 트랜잭션 안에서 던지면 커밋된 뒤에만 발송되고, 롤백되면 발송되지 않는다.
 *
 * @author 한성현
 */
public record ContractMailEvent(MailType type, ContractMailData data) {
}
