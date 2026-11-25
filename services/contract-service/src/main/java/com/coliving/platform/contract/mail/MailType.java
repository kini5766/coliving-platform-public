package com.coliving.platform.contract.mail;

import com.coliving.platform.contract.enums.ContractStatus;

import java.util.Optional;

/**
 * 계약 서비스가 보내는 메일 종류. 값 하나 = 템플릿 하나 (ContractMailTemplate.type()).
 *
 * 새 메일 추가 방법 (예: 입주 D-day 알림)
 *   1) 여기에 값 추가 (MOVE_IN_D_DAY 등)
 *   2) ContractMailTemplate 구현체를 template 패키지에 @Component로 추가 → 빠뜨리면 서버가 뜨지 않음
 *   3) 보내는 곳에서 ContractMailEvent를 publishEvent
 *      - 상태 변경으로 생기는 메일: forTransition()에 한 줄 추가하면 ContractStatusEvents가 알아서 보낸다
 *      - 날짜로 생기는 메일(D-day): 스케줄러에서 publishEvent (트랜잭션 밖이어도 리스너가 바로 실행됨)
 *
 * @author 한성현
 */
public enum MailType {

    APPLICATION_APPROVED,   // 심사 승인 (SUBMITTED → APPROVED): 결제 기한 안내
    APPLICATION_REJECTED,   // 심사 반려 (SUBMITTED → REJECTED): 반려 사유
    PAYMENT_COMPLETED;      // 결제 완료 (APPROVED → PAID)

    /** 상태 변경 → 보낼 메일. 메일이 없는 변경이면 empty */
    public static Optional<MailType> forTransition(ContractStatus from, ContractStatus to) {
        if (from == ContractStatus.SUBMITTED && to == ContractStatus.APPROVED) return Optional.of(APPLICATION_APPROVED);
        if (from == ContractStatus.SUBMITTED && to == ContractStatus.REJECTED) return Optional.of(APPLICATION_REJECTED);
        if (from == ContractStatus.APPROVED && to == ContractStatus.PAID) return Optional.of(PAYMENT_COMPLETED);
        return Optional.empty();
    }
}
