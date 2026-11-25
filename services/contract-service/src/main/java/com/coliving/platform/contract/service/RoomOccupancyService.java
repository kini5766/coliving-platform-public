package com.coliving.platform.contract.service;

import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 호실별 오늘 입주 가능 여부 (매물 서비스가 gRPC로 부른다 → ContractOccupancyGrpcService).
 *
 * 오늘(한국 날짜)이 예약 기간(입주일~퇴실일)에 걸친 신청이 있으면 입주 불가.
 * 예약으로 치는 상태는 ContractStatus.BOOKING (= DB 제약 no_overlap_booking과 같음).
 *
 * @author 한성현
 */
@Service
public class RoomOccupancyService {

    public static final int MAX_ROOM_IDS = 1000;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ContractRepository contractRepository;

    public RoomOccupancyService(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    /** roomId → 오늘 입주 가능 여부. 요청 순서대로, 중복·null은 뺀다. 1000개 넘으면 IllegalArgumentException */
    @Transactional(readOnly = true)
    public Map<Long, Boolean> getAvailability(Collection<Long> roomIds) {
        List<Long> ids = roomIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() > MAX_ROOM_IDS) {
            throw new IllegalArgumentException("호실은 한 번에 " + MAX_ROOM_IDS + "개까지 조회할 수 있습니다.");
        }
        Map<Long, Boolean> result = new LinkedHashMap<>();
        if (ids.isEmpty()) return result;

        Set<Long> occupied = new HashSet<>(contractRepository.findOccupiedRoomIds(
                ids, ContractStatus.BOOKING, LocalDate.now(SEOUL)));
        for (Long id : ids) {
            result.put(id, !occupied.contains(id));
        }
        return result;
    }
}
