package com.coliving.platform.contract.dto.application;

import java.time.LocalDate;
import java.util.List;

/**
 * [1-1 다른 방 추천] GET /contract-service/apply/rooms/available
 *
 * 화면이 보낸 후보 호실 중 고른 기간(입주일 ~ 퇴실 예정일, 퇴실일 당일 포함)에 예약이 없는 호실 ID.
 * 순서는 화면이 보낸 순서 그대로다 (같은 건물 → 같은 지역 같은 추천 순서를 화면이 정한다).
 * 누가 예약했는지는 내려주지 않는다.
 *
 * @param moveOutDate 퇴실 예정일. 서버가 계산한다 (입주일 + 개월 - 1일)
 * @author 한성현
 */
public record AvailableRoomsResponse(
        LocalDate moveInDate,
        LocalDate moveOutDate,
        List<Long> availableRoomIds
) {}
