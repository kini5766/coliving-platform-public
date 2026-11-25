package com.coliving.platform.contract.dto.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * [1-1 호실 선택 → 다음 단계] POST /contract-service/apply/applications
 *
 * DRAFT(작성중, 기한 2일) 신청서를 만든다.
 * 호실 정보(room)는 화면이 매물 서비스 REST에서 받은 값을 그대로 보낸다.
 *   - GET /property-service/property/rooms/{roomId} (Confluence「매물 사용자 API (계약화면)」)
 *   - 지역(region)은 받지 않는다 (2026-11-05 결정). 건물 주소(address)는 계약서 본문에 들어가므로 필수
 *   (매물 서비스에 계약용 호실 gRPC가 없어서 2026-11-05 변경. 이전: 계약 서비스가 gRPC로 조회)
 * ⚠ 금액도 화면이 보낸 값이다. 서버는 0 이하 같은 형식 오류만 막고, 실제 금액이 맞는지는
 *   관리자 승인 화면(5-2 상세)에서 확인한다 (팀 결정 2026-11-05).
 *
 * 보내지 않는 값과 이유
 *   - userId : Gateway가 넣는 X-User 헤더로 받는다
 *   - moveOutDate, firstPayment : 서버가 계산한다 (입주일 + 개월 - 1일, 첫 달 이용료 + 보증금)
 * "상담 신청" 버튼은 DRAFT를 만들지 않으므로 이 API를 부르지 않는다.
 *
 * @param moveInDate 입주일 (예: "2026-11-02")
 * @param months     계약 개월 수 (3 / 6 / 12)
 * @param room       신청 시점의 호실 스냅샷
 * @author 한성현
 */
public record ApplicationDraftRequest(
        Long roomId,
        LocalDate moveInDate,
        Integer months,
        RoomInfo room
) {

    /**
     * 화면이 매물 서비스에서 받아 보내는 호실 정보.
     * roomStructure 말고는 모두 필수다 (RoomSnapshot.from에서 검사).
     *
     * @param address        건물 주소 (계약서 buildingAddress 칸)
     * @param roomType       호실 유형 (예: "1인실")
     * @param roomStructure  선택 (없으면 null)
     * @param area           면적 (㎡, 예: 13.50)
     * @param roomFacilities 시설 목록 (예: ["전용 욕실"]). 없으면 빈 배열
     */
    public record RoomInfo(
            Long buildingId,
            String buildingName,
            String address,
            String roomName,
            String roomType,
            String roomStructure,
            Integer floorNumber,
            BigDecimal area,
            List<String> roomFacilities,
            Long monthlyFee,
            Long deposit,
            Long managementFee
    ) {}
}
