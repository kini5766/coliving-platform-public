package com.coliving.platform.contract.dto.internal;

import com.coliving.platform.contract.dto.application.ApplicationDraftRequest.RoomInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 신청서(Contract)의 호실 스냅샷 컬럼을 채울 때 쓰는 묶음.
 *
 * 화면(user-web)이 매물 서비스 REST에서 받은 호실 정보를 신청서 작성 요청(POST /apply/applications)의 room으로 보낸다.
 * (2026-11-05 변경: 매물 서비스에 계약용 호실 gRPC가 없어서)
 * 여기서는 형식(빈 값·길이)만 검사한다. 금액 범위는 Contract.applyRoomAndPeriod()가,
 * 금액이 실제 매물과 같은지는 관리자 승인 화면에서 확인한다.
 * roomStructure 말고는 모두 필수다 (Contract의 호실 스냅샷 컬럼이 NOT NULL).
 *
 * @param roomStructure  선택. 매물 쪽에 없을 수 있어 null 허용
 * @param roomFacilities 시설 목록을 쉼표로 이은 문자열 (예: "전용 욕실, 발코니"). 없으면 ""
 * @author 한성현
 */
public record RoomSnapshot(
        Long roomId,
        Long buildingId,
        String buildingName,
        String address,
        String roomName,
        String roomType,
        String roomStructure,
        int floorNumber,
        BigDecimal area,
        String roomFacilities,
        long monthlyFee,
        long deposit,
        long managementFee
) {

    /** 화면이 보낸 호실 정보 → 스냅샷. 빠지거나 형식이 틀리면 IllegalArgumentException(400) */
    public static RoomSnapshot from(Long roomId, RoomInfo r) {
        if (r == null) throw new IllegalArgumentException("호실 정보가 없습니다.");
        if (r.buildingId() == null) throw new IllegalArgumentException("건물 정보가 없습니다.");
        if (r.floorNumber() == null) throw new IllegalArgumentException("층 정보가 없습니다.");
        if (r.area() == null || r.area().signum() <= 0) throw new IllegalArgumentException("면적 정보가 올바르지 않습니다.");
        if (r.monthlyFee() == null || r.deposit() == null || r.managementFee() == null) {
            throw new IllegalArgumentException("금액 정보가 없습니다.");
        }
        // 금액 범위(0 이하·음수)는 Contract.applyRoomAndPeriod()가 검사한다

        return new RoomSnapshot(
                roomId,
                r.buildingId(),
                required(r.buildingName(), "건물명", 100),
                required(r.address(), "주소", 500),
                required(r.roomName(), "호실명", 100),
                required(r.roomType(), "호실 유형", 255),
                optional(r.roomStructure(), "호실 구조", 100),
                r.floorNumber(),
                r.area(),
                join(r.roomFacilities()),
                r.monthlyFee(),
                r.deposit(),
                r.managementFee()
        );
    }

    private static String required(String value, String label, int max) {
        String v = optional(value, label, max);
        if (v == null) throw new IllegalArgumentException(label + " 정보가 없습니다.");
        return v;
    }

    // 컬럼 길이를 넘으면 DB 오류(500) 대신 400으로 돌려준다
    private static String optional(String value, String label, int max) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.length() > max) throw new IllegalArgumentException(label + "이(가) 너무 깁니다. (최대 " + max + "자)");
        return v;
    }

    // ["전용 욕실", "발코니"] → "전용 욕실, 발코니". 없으면 "" (컬럼이 NOT NULL)
    private static String join(List<String> items) {
        if (items == null) return "";
        return String.join(", ", items.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }
}
