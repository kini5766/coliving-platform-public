package com.coliving.platform.contract.dto.contract;

import com.coliving.platform.contract.dto.internal.MyContractRow;
import com.coliving.platform.contract.enums.ContractStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * [내 계약서 목록] GET /contract-service/contracts 의 항목 1개
 *
 * 관리자 승인 뒤의 계약서만 나온다. 계약서는 제출 때 만들어지지만, 승인 전(작성 중·심사 중·반려·취소)은
 * 목록에서 뺀다 (MyContractService.BEFORE_APPROVAL).
 * status로 진행 상황을 보여준다 (APPROVED 결제 대기, COMPLETED 계약 완료, TERMINATED 종료 등).
 *
 * @param moveOutDate        퇴실 예정일 (입주일 + 개월 - 1일)
 * @param firstPaymentAmount 첫 결제 금액 (첫 달 이용료 + 보증금)
 * @param documentId         계약서 ID (contract_document.id)
 * @param language           계약서 언어 (ko / en)
 * @param issuedAt           계약서 생성 시각 (= 신청서 제출 시각)
 * @author 한성현
 */
public record MyContractListItemResponse(
        String applicationNo,
        ContractStatus status,

        String buildingName,
        String roomName,
        String roomType,

        LocalDate moveInDate,
        LocalDate moveOutDate,
        Integer months,

        Long monthlyFee,
        Long deposit,
        Long firstPaymentAmount,

        Long documentId,
        String language,
        OffsetDateTime issuedAt
) {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    public static MyContractListItemResponse from(MyContractRow r) {
        return new MyContractListItemResponse(
                r.applicationNo(),
                r.status(),
                r.buildingName(),
                r.roomName(),
                r.roomType(),
                r.moveInDate(),
                r.moveOutDate(),
                r.contractMonths() == null ? null : r.contractMonths().intValue(),
                r.monthlyFee(),
                r.deposit(),
                r.firstPayment(),
                r.documentId(),
                r.language(),
                r.issuedAt() == null ? null : r.issuedAt().atOffset(KST)
        );
    }
}
