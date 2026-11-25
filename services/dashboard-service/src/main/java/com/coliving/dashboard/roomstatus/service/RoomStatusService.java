package com.coliving.dashboard.roomstatus.service;

import com.coliving.dashboard.kafka.dto.ContractStatusChangedEvent;
import com.coliving.dashboard.property.BuildingRoomList;
import com.coliving.dashboard.property.PropertyRoomClient;
import com.coliving.dashboard.property.PropertyRoomsException;
import com.coliving.dashboard.roomstatus.document.RoomStatusDocument;
import com.coliving.dashboard.roomstatus.dto.RoomStatusResponse;
import com.coliving.dashboard.roomstatus.mapper.RoomStatusMapper;
import com.coliving.dashboard.roomstatus.repository.RoomStatusRepository;
import com.coliving.dashboard.snapshot.service.DashboardSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 계약 이벤트로 호실 상태를 갱신하고, 매물 서비스 호실 목록으로 칸을 맞춘 뒤 화면에 내려준다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomStatusService {

    private static final int ROOM_PAGE_SIZE = 1000;
    private static final String NEW_ROOM_STATUS = "VACANT";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RoomStatusRepository repository;
    private final PropertyRoomClient propertyRooms;
    private final ElasticsearchOperations operations;
    private final DashboardSnapshotService snapshotService;

    /** 계약 상태 변경 이벤트를 호실 상태에 반영한다. */
    public void applyContractEvent(ContractStatusChangedEvent event) {
        String roomStatus = RoomStatusMapper.toRoomStatus(event.currentStatus()).orElse(null);
        if (roomStatus == null) {
            log.warn("매핑되지 않은 계약 상태라 무시합니다: {} (eventId={})",
                    event.currentStatus(), event.eventId());
            return;
        }

        String id = String.valueOf(event.roomId());
        Instant occurredAt = event.occurredAt().toInstant();
        LocalDate today = LocalDate.now(KST);
        LocalDate moveInDate = parseDate(event.moveInDate());
        LocalDate moveOutDate = parseDate(event.moveOutDate());

        RoomStatusDocument existing = repository.findById(id).orElse(null);

        // 이 호실의 이전 계약이 퇴실 예정일을 넘겼는데 보증금을 아직 안 뺐다면, 문서가 덮어써지기 전에 먼저 뺀다.
        // (덮어쓰고 나면 이전 계약의 보증금과 퇴실 예정일을 알 수 없다)
        if (existing != null) {
            existing = snapshotService.releaseDueDeposit(existing);
        }

        // 늦게 도착한 오래된 이벤트가 최신 상태를 덮어쓰지 않도록 시간을 비교
        if (existing != null && existing.getUpdatedAt() != null
                && existing.getUpdatedAt().isAfter(occurredAt)) {
            log.info("더 최신 상태가 이미 있어 무시합니다: roomId={}", event.roomId());
            return;
        }

        // 다른 계약의 이벤트는 더 강한 상태를 덮지 못한다.
        // 예) 입주 중인 호실에 다른 사람의 신청·입주 예정·반려(공실) 이벤트가 와도 입주 상태가 유지된다.
        // 같은 계약의 이벤트는 항상 반영한다 (예: 입주 예정 → 환불 → 공실).
        if (existing != null && !Objects.equals(existing.getApplicationId(), event.applicationId())) {
            String current = RoomStatusMapper.resolve(existing, today);
            String incoming = RoomStatusMapper.resolve(roomStatus, moveInDate, moveOutDate, today);
            if (RoomStatusMapper.rank(incoming) < RoomStatusMapper.rank(current)) {
                log.info("다른 계약의 이벤트가 현재 상태({})를 덮을 수 없어 무시합니다: roomId={}, 이벤트 상태={}",
                        current, event.roomId(), incoming);
                return;
            }
        }

        // 보증금은 이 상태를 만든 계약의 값이다. 같은 계약의 이벤트면 이어받고, 다른 계약이면 새로 시작한다.
        boolean sameApplication = existing != null
                && Objects.equals(existing.getApplicationId(), event.applicationId());
        Long deposit = event.deposit() != null ? event.deposit()
                : (sameApplication ? existing.getDeposit() : null);
        Boolean depositReleased = sameApplication ? existing.getDepositReleased() : null;

        repository.save(RoomStatusDocument.builder()
                .id(id)
                .roomId(event.roomId())
                .buildingId(event.buildingId())
                .buildingName(event.buildingName())
                .floor(existing != null ? existing.getFloor() : null)   // 층은 계약 이벤트에 없으므로 기존 값 유지
                .roomName(event.roomName())
                .status(roomStatus)
                .applicationId(event.applicationId())
                .moveInDate(moveInDate)
                .moveOutDate(moveOutDate)
                .deposit(deposit)
                .depositReleased(depositReleased)
                .updatedAt(occurredAt)
                .build());
    }

    /** 건물별 호실 목록을 높은 층부터, 같은 층에서는 호실명 순으로 조회한다. 상태는 오늘 날짜를 반영해 계산한다. */
    public List<RoomStatusResponse> getRooms(Long buildingId) {
        if (buildingId != null) {
            syncFromProperty(buildingId);
        }
        LocalDate today = LocalDate.now(KST);
        return repository.findByBuildingId(buildingId, PageRequest.of(0, ROOM_PAGE_SIZE)).stream()
                .map(room -> RoomStatusResponse.from(room, today))
                .sorted(Comparator
                        .comparing(RoomStatusResponse::floor,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RoomStatusResponse::roomName,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * 매물 서비스의 호실 목록으로 칸을 맞춘다.
     * 새 호실은 공실로 두고, 이미 있는 호실은 입주 상태와 상태 시각을 유지한다.
     * 목록에서 빠진 호실은 삭제된 것으로 보고 지운다. 조회가 실패하면 저장된 값을 그대로 둔다.
     * 호실이 바뀌었으면 건물의 오늘 스냅샷도 다시 만든다. (새 건물이 다음 00:05까지 빈 화면이 되지 않게)
     */
    private void syncFromProperty(Long buildingId) {
        BuildingRoomList listed;
        try {
            listed = propertyRooms.listBuildingRooms(buildingId);
        } catch (PropertyRoomsException e) {
            log.warn("매물 서비스 호실 목록을 가져오지 못해 저장된 호실만 보여줍니다: buildingId={}", buildingId, e);
            return;
        }
        if (listed.buildingId() != buildingId) {
            log.warn("요청한 건물과 응답 건물이 다릅니다: requested={}, responded={}",
                    buildingId, listed.buildingId());
        }

        List<RoomStatusDocument> existing = repository.findByBuildingId(buildingId, PageRequest.of(0, ROOM_PAGE_SIZE));
        Map<Long, RoomStatusDocument> byRoomId = existing.stream()
                .filter(room -> room.getRoomId() != null)
                .collect(Collectors.toMap(RoomStatusDocument::getRoomId, Function.identity(), (left, right) -> left));

        Set<Long> incoming = new HashSet<>();
        boolean changed = false;
        for (BuildingRoomList.Room room : listed.rooms()) {
            incoming.add(room.roomId());
            RoomStatusDocument current = byRoomId.get(room.roomId());
            if (current == null) {
                repository.save(RoomStatusDocument.builder()
                        .id(String.valueOf(room.roomId()))
                        .roomId(room.roomId())
                        .buildingId(buildingId)
                        .buildingName(listed.buildingName())
                        .floor(room.floor())
                        .roomName(room.roomName())
                        .status(NEW_ROOM_STATUS)
                        .build());
                changed = true;
                continue;
            }
            if (sameRoomShape(current, listed.buildingName(), room)) {
                continue;
            }
            repository.save(RoomStatusDocument.builder()
                    .id(current.getId())
                    .roomId(current.getRoomId())
                    .buildingId(buildingId)
                    .buildingName(listed.buildingName())
                    .floor(room.floor())
                    .roomName(room.roomName())
                    .status(current.getStatus())
                    .applicationId(current.getApplicationId())   // 계약 ID와 날짜도 그대로 유지
                    .moveInDate(current.getMoveInDate())
                    .moveOutDate(current.getMoveOutDate())
                    .deposit(current.getDeposit())              // 보증금과 차감 표시도 그대로 유지
                    .depositReleased(current.getDepositReleased())
                    .updatedAt(current.getUpdatedAt())
                    .build());
            changed = true;
        }
        for (RoomStatusDocument room : existing) {
            if (room.getRoomId() == null || !incoming.contains(room.getRoomId())) {
                repository.delete(room);
                changed = true;
            }
        }
        if (changed) {
            operations.indexOps(RoomStatusDocument.class).refresh();
            refreshSnapshot(buildingId, listed.buildingName());
        }
    }

    /** 호실이 바뀐 건물의 오늘 스냅샷을 다시 만든다. 실패해도 호실 목록 응답은 막지 않는다. */
    private void refreshSnapshot(Long buildingId, String buildingName) {
        try {
            snapshotService.refreshToday(buildingId, buildingName);
        } catch (Exception e) {
            log.warn("호실 동기화 후 스냅샷 갱신에 실패했습니다: buildingId={}", buildingId, e);
        }
    }

    private static boolean sameRoomShape(RoomStatusDocument current, String buildingName, BuildingRoomList.Room room) {
        return Objects.equals(current.getBuildingName(), buildingName)
                && Objects.equals(current.getFloor(), room.floor())
                && Objects.equals(current.getRoomName(), room.roomName());
    }

    /** yyyy-MM-dd 글자를 날짜로 바꾼다. 비었거나 모양이 틀리면 null. */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}