package com.coliving.platform.contract.dto.application;

import com.coliving.platform.contract.entity.Contract;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * [1-2 신청자 정보] GET /contract-service/apply/applications/{applicationNo}/form
 *
 * DRAFT 신청서(DB에 저장된 호실·기간·금액)만 내려준다.
 * 신청자 정보(이름·휴대폰·이메일·주소·기본 계약 언어)는 프론트가
 * user-service GET /user-service/user/contract-autocomplete 에서 따로 불러와 채운다.
 * 본인 신청서가 아니면 403, DRAFT가 아니면 409.
 *
 * 호실 정보도 같이 내려주는 이유: 이 화면에서 새로고침해도 1-1 화면을 거치지 않고 그대로 보여주기 위해.
 *
 * @param draftExpiresAt     작성 기한 (생성 + 2일)
 * @param roomId             "이전" 버튼 → 1-1 화면으로 돌아갈 때 사용
 * @param moveOutDate        퇴실 예정일 (입주일 + 개월 - 1일)
 * @param firstPaymentAmount 첫 결제 금액 (첫 달 이용료 + 보증금, 서버 계산값)
 * @param contractLanguage   신청서에 고른 계약서 언어 (ko / en). 없으면 null → 프론트가 자동완성 값 또는 "ko"
 * @param termsVersion       지금 동의받을 약관 버전. 제출할 때 그대로 돌려보낸다
 * @author 한성현
 */
public record ApplyFormResponse(
        String applicationNo,
        OffsetDateTime draftExpiresAt,

        // 오른쪽 신청 정보 (DRAFT에 저장된 값)
        Long roomId,
        String buildingName,
        String roomName,
        String roomType,
        LocalDate moveInDate,
        LocalDate moveOutDate,
        Integer months,
        Long monthlyFee,
        Long managementFee,
        Long deposit,
        Long firstPaymentAmount,

        String contractLanguage,
        String termsVersion
) {

    public static ApplyFormResponse of(Contract c) {
        return new ApplyFormResponse(
                c.getApplicationNo(),
                c.getExpiresAt() == null ? null : c.getExpiresAt().atOffset(ZoneOffset.ofHours(9)),
                c.getRoomId(),
                c.getBuildingName(),
                c.getRoomName(),
                c.getRoomType(),
                c.getMoveInDate(),
                c.getMoveOutDate(),
                c.getContractMonths() == null ? null : c.getContractMonths().intValue(),
                c.getMonthlyFee(),
                c.getManagementFee(),
                c.getDeposit(),
                c.getFirstPayment(),
                c.getContractLanguage(),
                Contract.CURRENT_TERMS_VERSION
        );
    }
}
