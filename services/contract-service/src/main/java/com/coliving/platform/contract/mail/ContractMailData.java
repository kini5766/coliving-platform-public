package com.coliving.platform.contract.mail;

import com.coliving.platform.contract.entity.Contract;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 메일에 넣을 값의 스냅샷. 이벤트를 만드는 순간(트랜잭션 안)에 엔티티에서 복사해 둔다.
 * → 리스너는 커밋 후 다른 스레드에서 돌기 때문에 엔티티(지연 로딩, 영속성 컨텍스트)를 넘기지 않는다.
 *
 * @author 한성현
 */
public record ContractMailData(
        Long applicationId,
        String applicationNo,
        String toEmail,             // 제출 때 채워짐. 없으면 리스너가 건너뜀
        String applicantName,
        String buildingName,
        String roomName,
        LocalDate moveInDate,
        LocalDate moveOutDate,
        Long monthlyFee,
        Long deposit,
        Long firstPayment,          // 첫 결제 금액 (첫 달 이용료 + 보증금 - 할인)
        String rejectReason,        // 반려일 때만
        Instant expiresAt,          // 승인일 때 결제 기한
        String language             // 계약서 언어 ko/en (지금 템플릿은 한국어만)
) {
    public static ContractMailData from(Contract c) {
        return new ContractMailData(
                c.getId(), c.getApplicationNo(), c.getApplicantEmail(), c.getApplicantName(),
                c.getBuildingName(), c.getRoomName(), c.getMoveInDate(), c.getMoveOutDate(),
                c.getMonthlyFee(), c.getDeposit(), c.getFirstPayment(),
                c.getRejectReason(), c.getExpiresAt(), c.getContractLanguage());
    }
}
