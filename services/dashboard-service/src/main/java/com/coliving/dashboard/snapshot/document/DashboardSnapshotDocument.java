package com.coliving.dashboard.snapshot.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 일별 집계 스냅샷. 건물별로 하루 1건을 저장하며, 같은 날 다시 저장하면 덮어쓴다. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "dashboard-snapshot", createIndex = false)
public class DashboardSnapshotDocument {

    @Id
    private String id;                                   // 스냅샷 ID = {building_id}_{snapshot_date} (예: 1_2026-10-26)

    @Field(name = "building_id", type = FieldType.Long)
    private Long buildingId;                             // 건물 ID

    @Field(name = "building_name", type = FieldType.Keyword)
    private String buildingName;                         // 건물명

    @Field(name = "snapshot_date", type = FieldType.Date, format = DateFormat.date)
    private LocalDate snapshotDate;                      // 집계 기준일

    @Field(name = "total_rooms", type = FieldType.Integer)
    private Integer totalRooms;                          // 전체 호실 수

    @Field(name = "occupied_rooms", type = FieldType.Integer)
    private Integer occupiedRooms;                       // 입주 호실 수 (퇴거 예정 포함)

    @Field(name = "occupancy_rate", type = FieldType.Scaled_Float, scalingFactor = 100)
    private Double occupancyRate;                        // 입주율

    @Field(name = "resident_count", type = FieldType.Integer)
    private Integer residentCount;                       // 입주자 수

    @Field(name = "moving_out_count", type = FieldType.Integer)
    private Integer movingOutCount;                      // 퇴거 예정 호실 수

    @Field(name = "move_in_scheduled_count", type = FieldType.Integer)
    private Integer moveInScheduledCount;                // 입주 예정 호실 수 (옛 스냅샷에는 없어서 null일 수 있다)

    @Field(name = "pending_approval_count", type = FieldType.Integer)
    private Integer pendingApprovalCount;                // 계약 승인 대기 신청 수

    @Field(name = "pending_reservation_count", type = FieldType.Integer)
    private Integer pendingReservationCount;             // (더 이상 쓰지 않음)

    @Field(name = "overdue_count", type = FieldType.Integer)
    private Integer overdueCount;                        // (더 이상 쓰지 않음)

    @Field(name = "month_collected_amount", type = FieldType.Long)
    private Long monthCollectedAmount;                   // 당월 누적 수납액 (이용료 기준, 보증금 제외)

    @Field(name = "month_unpaid_amount", type = FieldType.Long)
    private Long monthUnpaidAmount;                      // (더 이상 쓰지 않음)

    @Field(name = "total_unpaid_amount", type = FieldType.Long)
    private Long totalUnpaidAmount;                      // (더 이상 쓰지 않음)

    @Field(name = "deposit_held_amount", type = FieldType.Long)
    private Long depositHeldAmount;                      // 보증금 보관액

    @Field(name = "month_refunded_amount", type = FieldType.Long)
    private Long monthRefundedAmount;                    // 당월 환불 완료 금액 (이용료 기준, 보증금 제외)

    @Field(name = "month_refunded_count", type = FieldType.Integer)
    private Integer monthRefundedCount;                  // 당월 환불 완료 건수

    @Field(name = "applied_event_keys", type = FieldType.Keyword)
    private List<String> appliedEventKeys;               // 당월 집계에 이미 반영한 결제·환불 키 (PAYMENT-{결제 ID}, REFUND-{결제 ID}). 같은 이벤트를 두 번 더하지 않기 위해 쓴다

    @Field(name = "created_at", type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;                           // 최초 저장 시간

    @Field(name = "updated_at", type = FieldType.Date, format = DateFormat.date_time)
    private Instant updatedAt;                           // 마지막 저장 시간
}