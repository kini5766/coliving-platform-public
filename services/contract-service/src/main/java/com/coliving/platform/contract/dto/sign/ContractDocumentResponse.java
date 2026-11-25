package com.coliving.platform.contract.dto.sign;

import java.time.LocalDate;

/**
 * [2 계약서 확인·서명] GET /contract-service/contracts/{applicationNo}/document (MyContractController)
 *
 * 관리자 승인 뒤부터 조회 가능. 승인 전(작성 중·심사 중·반려·취소)이면 계약서가 없는 것처럼 404.
 * 계약서는 contract_document에 저장된 완성본이다 (신청서 제출 때 생성).
 *
 * @param language           계약서 언어 ("ko" / "en")
 * @param content            계약서 본문 (HTML). 계약일은 결제 완료일로 채우고, 결제 전이면 빈칸
 * @param contentHash        저장본 기준 SHA-256. 서명할 때 그대로 돌려보낸다
 * @param moveOutDate        퇴실 예정일 (입주일 + 개월 - 1일)
 * @param firstPaymentAmount 첫 결제 금액 (첫 달 이용료 + 보증금)
 * @param identityVerified   본인인증 완료 여부. 지금은 signed와 같은 값 ("인증만 하고 서명 전" 상태를 저장하지 않아서)
 * @param signed             서명 완료 여부
 * @author 한성현
 */
public record ContractDocumentResponse(
        String applicationNo,

        // 계약서 뷰어
        Long documentId,
        String language,
        String content,
        String contentHash,

        // 오른쪽 신청 정보
        String buildingName,
        String roomName,
        LocalDate moveInDate,
        LocalDate moveOutDate,
        Long firstPaymentAmount,

        // 버튼 활성화 조건 (새로고침해도 진행 상태 유지)
        boolean identityVerified,
        boolean signed
) {}
