package com.coliving.dashboard.roomstatus.mapper;

import com.coliving.dashboard.roomstatus.document.RoomStatusDocument;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** 계약 상태를 호실 상태로 변환하고, 저장된 호실 상태를 오늘 기준의 실제 상태로 계산한다. */
public final class RoomStatusMapper {

    /** 입주 중인 호실이 퇴실 예정일 며칠 전부터 "퇴거 예정"으로 보일지. */
    public static final int MOVE_OUT_SOON_DAYS = 14;

    // 계약 상태 → 호실 상태 (저장용)
    private static final Map<String, String> MAPPING = Map.ofEntries(
            Map.entry("SUBMITTED", "APPLYING"),               // 신청 진행 중
            Map.entry("APPROVED", "APPLYING"),                // 신청 진행 중
            Map.entry("PAID", "MOVE_IN_SCHEDULED"),           // 결제 완료, 입주 예정 (입주일이 되면 resolve가 입주로 계산)
            Map.entry("COMPLETED", "OCCUPIED"),               // 입주
            Map.entry("TERMINATION_REQUESTED", "MOVING_OUT"), // 퇴거 예정
            Map.entry("TERMINATED", "MOVING_OUT"),            // 퇴거 예정
            Map.entry("REJECTED", "VACANT"),                  // 공실
            Map.entry("CANCELED", "VACANT"),                  // 공실
            Map.entry("EXPIRED", "VACANT"),                   // 공실
            Map.entry("REFUNDED", "VACANT")                   // 공실
    );

    private RoomStatusMapper() {}   // static 메서드만 쓰는 도구 클래스라 객체 생성을 막음

    /** 계약 상태에 해당하는 호실 상태를 반환한다. 매핑이 없으면 빈 값. */
    public static Optional<String> toRoomStatus(String contractStatus) {
        return Optional.ofNullable(MAPPING.get(contractStatus));
    }

    /** 저장된 호실 상태를 오늘 기준의 실제 상태로 계산한다. */
    public static String resolve(RoomStatusDocument doc, LocalDate today) {
        return resolve(doc.getStatus(), doc.getMoveInDate(), doc.getMoveOutDate(), today);
    }

    /**
     * 저장된 상태와 날짜로 오늘의 실제 상태를 계산한다.
     *  - 입주 예정이어도 입주일이 오늘이거나 지났으면 입주 (계약 서비스가 COMPLETED로 바꿀 때까지 기다리지 않는다)
     *  - 입주 중인데 퇴실 예정일이 지났으면 공실
     *  - 입주 중인데 퇴실 예정일이 MOVE_OUT_SOON_DAYS일 안이면 퇴거 예정
     * 날짜가 없는 옛 데이터는 저장된 상태를 그대로 쓴다.
     */
    public static String resolve(String status, LocalDate moveInDate, LocalDate moveOutDate, LocalDate today) {
        if (status == null) return "VACANT";

        String result = status;
        if ("MOVE_IN_SCHEDULED".equals(result) && moveInDate != null && !moveInDate.isAfter(today)) {
            result = "OCCUPIED";
        }
        if ("OCCUPIED".equals(result) || "MOVING_OUT".equals(result)) {
            if (moveOutDate != null && moveOutDate.isBefore(today)) {
                return "VACANT";
            }
            if ("OCCUPIED".equals(result) && moveOutDate != null
                    && !moveOutDate.isAfter(today.plusDays(MOVE_OUT_SOON_DAYS))) {
                return "MOVING_OUT";
            }
        }
        return result;
    }

    /** 다른 계약의 이벤트가 덮어쓸 수 있는지 비교하는 강도. 높은 상태는 낮은 상태가 덮지 못한다. */
    public static int rank(String status) {
        if (status == null) return 0;
        return switch (status) {
            case "OCCUPIED", "MOVING_OUT" -> 3;
            case "MOVE_IN_SCHEDULED" -> 2;
            case "APPLYING" -> 1;
            default -> 0;
        };
    }

    /** 호실명에서 끝 두 자리를 뺀 앞부분을 층으로 사용한다. */
    public static Integer guessFloor(String roomName) {
        if (roomName == null) return null;
        String digits = roomName.replaceAll("\\D", "");
        if (digits.length() < 3 || digits.length() > 6) return null;
        return Integer.parseInt(digits.substring(0, digits.length() - 2));
    }
}