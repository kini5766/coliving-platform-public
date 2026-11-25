package com.coliving.platform.contract.dto.application;

import java.time.LocalDate;

/**
 * [1-1 호실 선택] GET /contract-service/apply/rooms/{roomId}/booked-periods 의 항목 1개
 *
 * 이 호실에 이미 잡혀 있는 기간. 신청자 정보(누가 신청했는지)는 내려주지 않는다.
 * 화면은 이 기간과 겹치는 입주일·기간을 고르지 못하게 막는다 (서버도 no_overlap_booking으로 한 번 더 막음).
 *
 * @param moveOutDate 퇴실일. 당일까지 사용 중 (다음 날부터 입주 가능)
 * @author 한성현
 */
public record BookedPeriodResponse(
        LocalDate moveInDate,
        LocalDate moveOutDate
) {}
