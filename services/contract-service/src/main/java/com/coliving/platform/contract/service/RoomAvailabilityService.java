package com.coliving.platform.contract.service;

import com.coliving.platform.contract.dto.application.AvailableRoomsResponse;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 1-1 호실 선택 화면의 "다른 방 추천"용 빈 방 확인.
 *
 * 후보 호실 목록(같은 건물·같은 지역)은 화면이 매물 서비스에서 받아 보낸다.
 * 계약 서비스는 그중 고른 기간에 예약이 없는 호실만 골라 준다 (쿼리 1번).
 *
 * 이 결과는 안내용이다. 실제로 신청할 때는 no_overlap_booking 제약이 한 번 더 막는다
 * (추천을 본 사이에 다른 사람이 먼저 신청할 수 있음).
 *
 * @author 한성현
 */
@Service
public class RoomAvailabilityService {

    static final int MAX_ROOM_IDS = 200;          // 한 번에 확인할 후보 수 상한 (URL 길이·IN 절 크기)
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ContractRepository contractRepository;

    public RoomAvailabilityService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    @Transactional(readOnly = true)
    public AvailableRoomsResponse findAvailable(Long userId, List<Long> roomIds, LocalDate moveInDate, Integer months) {
        if (moveInDate == null) throw new IllegalArgumentException("입주일을 선택해주세요.");
        if (months == null || !ApplicationService.ALLOWED_MONTHS.contains(months)) {   // 신청과 같은 규칙 (3·6·12개월)
            throw new IllegalArgumentException("계약 기간이 올바르지 않습니다.");
        }
        if (moveInDate.isBefore(LocalDate.now(SEOUL))) {
            throw new IllegalArgumentException("지난 날짜는 선택할 수 없습니다.");
        }

        // 중복·null 제거, 화면이 보낸 순서 유지
        Set<Long> candidates = new LinkedHashSet<>();
        if (roomIds != null) {
            for (Long id : roomIds) if (id != null) candidates.add(id);
        }
        if (candidates.size() > MAX_ROOM_IDS) {
            throw new IllegalArgumentException("한 번에 확인할 수 있는 호실은 " + MAX_ROOM_IDS + "개까지입니다.");
        }

        LocalDate moveOutDate = moveInDate.plusMonths(months).minusDays(1);   // Contract와 같은 계산
        if (candidates.isEmpty()) {
            return new AvailableRoomsResponse(moveInDate, moveOutDate, List.of());
        }

        Set<Long> booked = new HashSet<>(contractRepository.findBookedRoomIds(
                candidates, ContractStatus.BOOKING, moveInDate, moveOutDate, userId == null ? -1L : userId));

        List<Long> available = candidates.stream().filter(id -> !booked.contains(id)).toList();
        return new AvailableRoomsResponse(moveInDate, moveOutDate, available);
    }
}
