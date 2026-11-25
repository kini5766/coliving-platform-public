package com.coliving.platform.contract.dto.internal;

import com.coliving.platform.contract.enums.ContractStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 내 계약서 목록 쿼리 결과 1행 (ContractDocumentRepository.findMyContracts의 select new 대상).
 * 필드 타입은 엔티티 필드 타입과 똑같아야 한다 (contractMonths = Short, issuedAt = Instant).
 * 건물·호실·금액은 신청 당시 스냅샷이다. 화면용 변환은 MyContractListItemResponse.from()에서 한다.
 *
 * @param moveOutDate  퇴실 예정일 (입주일 + 개월 - 1일)
 * @param firstPayment 첫 결제 금액 (첫 달 이용료 + 보증금)
 * @param documentId   계약서 ID (contract_document.id)
 * @param issuedAt     계약서 생성 시각 (contract_document.created_at = 신청서 제출 시각)
 * @author 한성현
 */
public record MyContractRow(
        String applicationNo,
        ContractStatus status,
        String buildingName,
        String roomName,
        String roomType,
        LocalDate moveInDate,
        LocalDate moveOutDate,
        Short contractMonths,
        Long monthlyFee,
        Long deposit,
        Long firstPayment,
        Long documentId,
        String language,
        Instant issuedAt
) {}
