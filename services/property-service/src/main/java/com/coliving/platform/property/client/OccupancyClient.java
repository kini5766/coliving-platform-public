package com.coliving.platform.property.client;

import com.coliving.platform.grpc.contract.ContractOccupancyServiceGrpc;
import com.coliving.platform.grpc.contract.GetRoomAvailabilityRequest;
import com.coliving.platform.grpc.contract.RoomAvailability;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 입주 가능 여부는 매물 DB에 저장하지 않고 계약 서비스에서 가져온다(확인 항목 2).
 *   계약 서비스 gRPC ContractOccupancyService.GetRoomAvailability (contract_occupancy_service.proto, 9089)
 *   기준: 오늘(한국 날짜)이 걸친 예약(작성 중·심사 중·결제 대기·결제·계약 완료 등)이 없으면 입주 가능
 *
 * 계약 서비스가 꺼져 있거나 느리면(2초) 예외를 던지지 않고 빈 Map("알 수 없음")을 돌려준다.
 *   → 화면은 입주 가능 표시만 숨기고, 매물 목록·상세는 정상으로 보인다.
 */
@Component
public class OccupancyClient {

    private static final Logger log = LoggerFactory.getLogger(OccupancyClient.class);
    private static final int BATCH = 1000;   // 계약 서비스 한 번 요청 최대 개수 (RoomOccupancyService.MAX_ROOM_IDS)

    private final ContractOccupancyServiceGrpc.ContractOccupancyServiceBlockingStub stub;

    public OccupancyClient(ContractOccupancyServiceGrpc.ContractOccupancyServiceBlockingStub stub) {
        this.stub = stub;
    }

    /** roomId → 입주 가능 여부. 계약 서비스에 연결하지 못하면 빈 Map. */
    public Map<Long, Boolean> getRoomAvailability(Collection<Long> roomIds) {
        List<Long> ids = roomIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();

        Map<Long, Boolean> result = new HashMap<>();
        try {
            for (int from = 0; from < ids.size(); from += BATCH) {
                List<Long> chunk = ids.subList(from, Math.min(from + BATCH, ids.size()));
                for (RoomAvailability r : stub.getRoomAvailability(
                        GetRoomAvailabilityRequest.newBuilder().addAllRoomIds(chunk).build()).getRoomsList()) {
                    result.put(r.getRoomId(), r.getAvailable());
                }
            }
        } catch (StatusRuntimeException e) {
            log.warn("계약 서비스 입주 가능 여부 조회 실패 → 표시 숨김. status={}, rooms={}", e.getStatus(), ids.size());
            return Map.of();
        }
        return result;
    }

    public Optional<Boolean> isAvailable(Long roomId) {
        return Optional.ofNullable(getRoomAvailability(List.of(roomId)).get(roomId));
    }
}
