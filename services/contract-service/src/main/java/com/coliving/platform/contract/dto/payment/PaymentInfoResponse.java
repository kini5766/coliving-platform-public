package com.coliving.platform.contract.dto.payment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * [3 결제 화면 진입] GET /contract-service/apply/applications/{applicationNo}/payment
 *
 * 결제 버튼을 누르기 전에 화면에 보여줄 값.
 * 서명 완료 + APPROVED + 기한 안일 때만 조회 가능 (그 외 409).
 * 주문번호(orderId)는 여기서 주지 않고, 결제 준비(ready) 때 만든다.
 *
 * @param firstMonthFee    첫 달 이용료
 * @param totalAmount      결제 금액 (deposit + firstMonthFee)
 * @param moveOutDate      퇴실 예정일 (입주일 + 개월 - 1일)
 * @param availableMethods 지금 쓸 수 있는 결제 수단 (지금은 ["KAKAOPAY"]). 프론트는 이 목록대로 버튼을 그린다
 * @param paymentDeadline  결제 기한. 남은 시간 = paymentDeadline - serverTime
 * @param serverTime       응답 시점의 서버 시각. 사용자 PC 시계 대신 이 값 기준으로 카운트다운한다 (받은 순간부터 1초씩 줄임)
 * @author 한성현
 */
public record PaymentInfoResponse(
        // 결제 금액
        Long deposit,
        Long firstMonthFee,
        Long totalAmount,

        // 오른쪽 신청 정보
        String buildingName,
        String roomName,
        Long managementFee,
        LocalDate moveInDate,
        LocalDate moveOutDate,

        // 결제 수단·남은 시간
        List<String> availableMethods,

        OffsetDateTime paymentDeadline,
        OffsetDateTime serverTime
) {}
