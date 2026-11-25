package com.coliving.dashboard.snapshot.service;

import com.coliving.dashboard.activitylog.document.ActivityLogDocument;
import com.coliving.dashboard.activitylog.repository.ActivityLogRepository;
import com.coliving.dashboard.roomstatus.document.RoomStatusDocument;
import com.coliving.dashboard.roomstatus.mapper.RoomStatusMapper;
import com.coliving.dashboard.roomstatus.repository.RoomStatusRepository;
import com.coliving.dashboard.snapshot.document.DashboardSnapshotDocument;
import com.coliving.dashboard.snapshot.repository.DashboardSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 일별 집계 스냅샷을 만든다. 건물별로 하루 1건({건물ID}_{날짜})이며, 같은 날 다시 만들면 덮어쓴다.
 *
 * 호실 현황(입주율, 입주 예정, 퇴거 예정)은 저장된 호실 상태에 오늘 날짜를 반영해 계산한다.
 * 계약 승인 대기 건수는 활동 로그로 계산한다.
 * 당월 수납액은 결제 완료 이벤트, 당월 환불 금액·건수는 환불 완료 이벤트로 쌓는다.
 * 수납액과 환불 금액은 이용료 기준이다. 이벤트 금액에서 보증금을 뺀 값을 더한다.
 * 보증금 보관액은 결제 완료 이벤트의 보증금을 더하고, 환불 완료 이벤트의 보증금을 빼서 이어간다.
 * 퇴실 이벤트는 따로 오지 않으므로, 호실 현황의 퇴실 예정일이 지나면 그 계약의 보증금을 보증금 보관액에서 뺀다.
 * 서비스가 뜰 때와 매일 00:05에 확인하고, 호실 문서가 다른 계약으로 덮어써지기 직전에도 확인한다.
 * 같은 결제·환불·퇴실이 다시 처리돼도 두 번 더하거나 빼지 않도록, 반영한 키를 금액과 같은 스냅샷 문서에 함께 저장한다.
 * 퇴실은 호실 문서에도 "뺐음" 표시를 남겨서, 월이 바뀌어 키가 지워져도 다시 빼지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardSnapshotService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 결제가 끝나 보증금이 보관액에 들어간 계약의 호실 상태. 환불로 공실이 된 호실(VACANT)은 환불 이벤트로 이미 뺐으므로 제외한다.
    private static final Set<String> DEPOSIT_HELD_STATUSES = Set.of("MOVE_IN_SCHEDULED", "OCCUPIED", "MOVING_OUT");

    private final DashboardSnapshotRepository snapshotRepository;
    private final RoomStatusRepository roomStatusRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ElasticsearchOperations operations;

    /** 계약·예약 이벤트 등으로 현황이 바뀐 뒤, 건물의 오늘 스냅샷을 다시 만든다. */
    public void refreshToday(Long buildingId, String buildingName) {
        saveToday(buildingId, buildingName, 0L, 0L, 0, 0L, null);
    }

    /**
     * 결제 완료 이벤트의 이용료(금액 - 보증금)를 오늘 스냅샷의 당월 수납액에, 보증금을 보증금 보관액에 더한다.
     * 같은 결제 ID를 다시 받으면 이미 반영한 것으로 보고 아무것도 바꾸지 않는다.
     */
    public void addPayment(Long paymentId, Long buildingId, String buildingName, long amount, Long deposit) {
        saveToday(buildingId, buildingName, feeOf(amount, deposit), 0L, 0, orZero(deposit), "PAYMENT-" + paymentId);
    }

    /**
     * 환불 완료 이벤트의 이용료(금액 - 보증금)와 건수를 오늘 스냅샷의 당월 환불에 더하고, 보증금은 보증금 보관액에서 뺀다.
     * 같은 결제 ID의 환불을 다시 받으면 이미 반영한 것으로 보고 아무것도 바꾸지 않는다.
     */
    public void addRefund(Long paymentId, Long buildingId, String buildingName, long amount, Long deposit) {
        saveToday(buildingId, buildingName, 0L, feeOf(amount, deposit), 1, -orZero(deposit), "REFUND-" + paymentId);
    }

    /**
     * 퇴실 예정일이 지난 호실의 보증금을 오늘 스냅샷의 보증금 보관액에서 빼고, 그 호실 문서에 "뺐음" 표시를 남긴다.
     * 뺄 게 없으면 아무것도 하지 않고 받은 문서를 그대로 돌려준다. 뺐으면 표시가 붙은 문서를 돌려준다.
     *
     * 순서: 스냅샷에서 먼저 빼고(같은 계약은 키로 한 번만), 그다음 호실 문서에 표시한다.
     * 표시 저장이 실패해 다시 호출돼도, 같은 달이면 키가 막아주므로 두 번 빠지지 않는다.
     * 표시는 일부 필드만 바꾸는 부분 업데이트라서, 그사이 다른 이벤트가 쓴 호실 값을 덮어쓰지 않는다.
     */
    public synchronized RoomStatusDocument releaseDueDeposit(RoomStatusDocument room) {
        LocalDate today = LocalDate.now(KST);
        if (!isDepositReleaseDue(room, today)) {
            return room;
        }
        // 다른 스레드가 방금 처리했을 수 있으니 최신 문서로 한 번 더 확인한다.
        RoomStatusDocument fresh = roomStatusRepository.findById(room.getId()).orElse(null);
        if (fresh == null || !isDepositReleaseDue(fresh, today)) {
            return fresh != null ? fresh : room;
        }

        saveToday(fresh.getBuildingId(), fresh.getBuildingName(), 0L, 0L, 0,
                -fresh.getDeposit(), "DEPOSIT-RELEASE-" + fresh.getApplicationId());

        operations.update(
                UpdateQuery.builder(fresh.getId())
                        .withDocument(Document.from(Map.of("deposit_released", true)))
                        .build(),
                operations.getIndexCoordinatesFor(RoomStatusDocument.class));
        log.info("퇴실 보증금을 보관액에서 뺐습니다: roomId={}, applicationId={}, deposit={}",
                fresh.getRoomId(), fresh.getApplicationId(), fresh.getDeposit());
        return fresh.toBuilder().depositReleased(true).build();
    }

    /**
     * 퇴실 예정일이 지났는데 보증금을 아직 안 뺀 호실인지. RoomStatusMapper.resolve가 공실로 보는 시점(예정일 다음 날)과 같다.
     * 보증금이 기록되지 않은 옛 호실 문서는 얼마를 뺄지 모르므로 대상이 아니다.
     */
    private static boolean isDepositReleaseDue(RoomStatusDocument room, LocalDate today) {
        return room.getApplicationId() != null
                && room.getDeposit() != null && room.getDeposit() > 0
                && !Boolean.TRUE.equals(room.getDepositReleased())
                && room.getMoveOutDate() != null && room.getMoveOutDate().isBefore(today)
                && DEPOSIT_HELD_STATUSES.contains(room.getStatus());
    }

    /**
     * 서비스가 뜬 직후 모든 건물의 오늘 스냅샷을 한 번 만든다.
     * 00:05 스케줄러 시간에 서비스가 꺼져 있었거나 새 달이 시작된 뒤 이벤트가 없어도, 이번 달 요약이 비지 않게 한다.
     * 같은 날 여러 번 실행해도 수납액·환불액은 0을 더하는 호출이라 값이 변하지 않는다.
     * 서비스가 꺼져 있는 동안 퇴실일이 지난 호실의 보증금도 이때 함께 뺀다.
     * ES가 아직 준비되지 않았어도 서비스 기동은 실패시키지 않는다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            refreshAllToday();
        } catch (Exception e) {
            log.warn("기동 시 스냅샷 생성에 실패했습니다. 다음 이벤트나 00:05 실행, 또는 POST /admin/snapshots/refresh로 만들 수 있습니다.", e);
        }
    }

    /** 모든 건물의 오늘 스냅샷을 만든다. 날짜나 월이 바뀌어도 화면에 최신 스냅샷이 있게 매일 새벽에 실행한다. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void refreshAllToday() {
        List<RoomStatusDocument> rooms = new ArrayList<>();
        roomStatusRepository.findAll().forEach(rooms::add);

        // 퇴실 예정일이 지난 호실의 보증금을 먼저 뺀다. 한 호실이 실패해도 나머지는 계속한다.
        for (RoomStatusDocument room : rooms) {
            try {
                releaseDueDeposit(room);
            } catch (Exception e) {
                log.error("퇴실 보증금 차감 실패: roomId={}", room.getRoomId(), e);
            }
        }

        Map<Long, String> buildings = new LinkedHashMap<>();
        rooms.forEach(room -> {
            if (room.getBuildingId() != null) {
                buildings.putIfAbsent(room.getBuildingId(), room.getBuildingName());
            }
        });
        int success = 0;
        for (Map.Entry<Long, String> entry : buildings.entrySet()) {
            try {
                refreshToday(entry.getKey(), entry.getValue());
                success++;
            } catch (Exception e) {
                // 건물 하나가 실패해도 나머지 건물은 계속 만든다.
                log.error("스냅샷 생성 실패: buildingId={}", entry.getKey(), e);
            }
        }
        log.info("일별 스냅샷 생성 완료: 건물 {}곳 중 {}곳", buildings.size(), success);
    }

    // 계약 이벤트와 결제·환불 이벤트가 동시에 들어와도 금액이 덮어써지지 않도록 한 번에 하나씩 처리한다.
    // eventKey: 결제·환불·퇴실이면 반영 여부를 가리는 키(PAYMENT-{id}, REFUND-{id}, DEPOSIT-RELEASE-{계약 ID}), 단순 갱신이면 null
    private synchronized void saveToday(Long buildingId, String buildingName,
                                        long paymentFee, long refundFee, int refundCount,
                                        long depositDelta, String eventKey) {
        // 방금 저장한 문서도 검색에 잡히도록 인덱스를 새로고침한다 (ES는 저장 직후 바로 검색되지 않을 수 있다).
        operations.indexOps(RoomStatusDocument.class).refresh();
        operations.indexOps(ActivityLogDocument.class).refresh();

        LocalDate today = LocalDate.now(KST);
        Instant now = Instant.now();

        // 오늘 스냅샷이 있으면 그것, 없으면 가장 최근 스냅샷
        // 오늘 문서는 ID로 바로 읽는다. ID 조회는 저장 직후에도 최신 값이 보이지만,
        // 검색은 저장 직후 잠시 동안 방금 저장한 값을 못 볼 수 있다 (그러면 방금 더한 금액이 사라진다).
        String todayId = buildingId + "_" + today;
        DashboardSnapshotDocument latest = snapshotRepository.findById(todayId)
                .or(() -> snapshotRepository
                        .findFirstByBuildingIdAndSnapshotDateLessThanEqualOrderBySnapshotDateDesc(buildingId, today))
                .orElse(null);
        boolean sameDay = latest != null && latest.getSnapshotDate().equals(today);
        boolean sameMonth = latest != null
                && YearMonth.from(latest.getSnapshotDate()).equals(YearMonth.from(today));

        // 이번 달 집계에 이미 반영한 결제·환불·퇴실 키. 같은 키가 다시 오면 아무것도 바꾸지 않고 끝낸다.
        // 키는 금액과 같은 문서에 함께 저장되므로, 금액만 반영되거나 키만 남는 일이 생기지 않는다.
        List<String> appliedKeys = new ArrayList<>();
        if (sameMonth && latest.getAppliedEventKeys() != null) {
            appliedKeys.addAll(latest.getAppliedEventKeys());
        }
        if (eventKey != null) {
            if (appliedKeys.contains(eventKey)) {
                log.info("이미 집계에 반영한 이벤트라 건너뜁니다: key={}", eventKey);
                return;
            }
            appliedKeys.add(eventKey);
        }

        // 호실 현황으로 계산하는 값: 저장된 상태가 아니라 오늘 날짜를 반영한 실제 상태로 센다.
        List<RoomStatusDocument> rooms = roomStatusRepository.findByBuildingId(buildingId, PageRequest.of(0, 1000));
        List<String> statuses = rooms.stream().map(r -> RoomStatusMapper.resolve(r, today)).toList();
        int total = rooms.size();
        int movingOut = countStatus(statuses, "MOVING_OUT");            // 퇴거 예정
        int moveInScheduled = countStatus(statuses, "MOVE_IN_SCHEDULED"); // 입주 예정
        // 입주율은 퇴거 예정 호실도 입주로 본다 (es-setup.md의 더미 데이터와 같은 기준). 입주 예정은 아직 입주가 아니다.
        int occupied = countStatus(statuses, "OCCUPIED") + movingOut;
        double occupancyRate = total == 0 ? 0.0 : Math.round(occupied * 1000.0 / total) / 10.0;

        // 활동 로그로 계산하는 값: 처리 대기 중인 계약 건
        int pendingApproval = (int) activityLogRepository
                .countByBuildingIdAndActivityTypeAndStatus(buildingId, "CONTRACT", "PENDING");
        int pendingReservation = (int) activityLogRepository
                .countByBuildingIdAndActivityTypeAndStatus(buildingId, "RESERVATION", "PENDING");

        // 당월 수납액(이용료)은 같은 달이면 이어받아 더한다. (환불은 빼지 않고 따로 집계한다)
        long collected = (sameMonth ? orZero(latest.getMonthCollectedAmount()) : 0L) + paymentFee;

        // 당월 환불 금액(이용료)·건수도 같은 달이면 이어받아 더한다.
        long refunded = (sameMonth ? orZero(latest.getMonthRefundedAmount()) : 0L) + refundFee;
        int refundedCount = (sameMonth ? orZero(latest.getMonthRefundedCount()) : 0) + refundCount;

        // 보증금 보관액은 월과 상관없는 누적 잔액이라, 직전 스냅샷 값에 이번 증감을 더한다.
        // 이 기능이 생기기 전의 결제는 보증금이 더해지지 않았으므로, 그 환불·퇴실 때문에 음수가 되지 않게 0에서 멈춘다.
        long depositHeld = Math.max(0L,
                (latest == null ? 0L : orZero(latest.getDepositHeldAmount())) + depositDelta);

        snapshotRepository.save(DashboardSnapshotDocument.builder()
                .id(todayId)
                .buildingId(buildingId)
                .buildingName(buildingName)
                .snapshotDate(today)
                .totalRooms(total)
                .occupiedRooms(occupied)
                .occupancyRate(occupancyRate)
                .residentCount(occupied)                 // 입주자는 1인 1실로 본다
                .movingOutCount(movingOut)
                .moveInScheduledCount(moveInScheduled)
                .pendingApprovalCount(pendingApproval)
                .monthCollectedAmount(collected)
                .depositHeldAmount(depositHeld)
                .monthRefundedAmount(refunded)
                .monthRefundedCount(refundedCount)
                .appliedEventKeys(appliedKeys)
                .createdAt(sameDay && latest.getCreatedAt() != null ? latest.getCreatedAt() : now)
                .updatedAt(now)
                .build());
    }

    /** 이벤트 금액에서 보증금을 뺀 이용료. 보증금이 없는 옛 메시지는 금액 그대로 쓰고, 음수는 0으로 막는다. */
    private static long feeOf(long amount, Long deposit) {
        return Math.max(0L, amount - orZero(deposit));
    }

    /** 해당 상태인 호실 수. */
    private static int countStatus(List<String> statuses, String status) {
        return (int) statuses.stream().filter(status::equals).count();
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}