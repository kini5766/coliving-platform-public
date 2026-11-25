package com.coliving.platform.contract.repository;

import com.coliving.platform.contract.dto.application.BookedPeriodResponse;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.enums.ContractStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * @author 한성현
 */
@Repository
public interface ContractRepository extends JpaRepository<Contract,Long> {

    Optional<Contract> findByApplicationNo(String applicationNo);

    // 같은 회원이 같은 호실로 만든 작성 중 신청서 (이전으로 돌아왔을 때 재사용)
    Optional<Contract> findByUserIdAndRoomIdAndStatus(Long userId, Long roomId, ContractStatus status);

    // community 2-1 입주 호실 조회: 여러 회원의 계약을 한 번에 (회원당 1개 고르기는 ResidentRoomService)
    List<Contract> findByUserIdInAndStatusIn(Collection<Long> userIds, Collection<ContractStatus> statuses);

    // 1-1 호실 선택: 이 호실에 잡혀 있는 기간 (퇴실일이 from 이후인 것만, 입주일 순)
    //   statuses = no_overlap_booking 대상 상태. 보는 사람 본인의 작성 중(DRAFT) 신청은 뺀다
    //   (같은 호실 DRAFT는 "다음 단계"에서 재사용되므로 본인에게는 막힌 기간이 아니다)
    //   excludeDraftUserId에 null을 넣으면 SQL에서 비교 결과가 NULL이 돼 전부 빠지므로, 비로그인은 -1을 넘긴다
    @Query("""
            select new com.coliving.platform.contract.dto.application.BookedPeriodResponse(c.moveInDate, c.moveOutDate)
              from Contract c
             where c.roomId = :roomId
               and c.status in :statuses
               and c.moveOutDate >= :from
               and not (c.status = com.coliving.platform.contract.enums.ContractStatus.DRAFT
                        and c.userId = :excludeDraftUserId)
             order by c.moveInDate
            """)
    List<BookedPeriodResponse> findBookedPeriods(@Param("roomId") Long roomId,
                                                 @Param("statuses") Collection<ContractStatus> statuses,
                                                 @Param("from") LocalDate from,
                                                 @Param("excludeDraftUserId") Long excludeDraftUserId);

    // 매물 서비스 gRPC(입주 가능 여부): roomIds 중 day가 예약 기간(입주일~퇴실일)에 걸친 호실 ID
    @Query("""
            select distinct c.roomId
              from Contract c
             where c.roomId in :roomIds
               and c.status in :statuses
               and c.moveInDate <= :day
               and c.moveOutDate >= :day
            """)
    List<Long> findOccupiedRoomIds(@Param("roomIds") Collection<Long> roomIds,
                                   @Param("statuses") Collection<ContractStatus> statuses,
                                   @Param("day") LocalDate day);

    // 나의 신청 목록: 한 회원의 신청 중 특정 상태(DRAFT)를 뺀 전부 (최근 신청 순)
    List<Contract> findByUserIdAndStatusNotOrderByCreatedAtDesc(Long userId, ContractStatus status);

    // 관리자 계약서 목록: 상태만으로 거르기 (최근 신청 순)
    List<Contract> findByStatusInOrderByCreatedAtDesc(Collection<ContractStatus> statuses);

    // 관리자 계약서 목록: 상태 + 검색어 (신청자 이름·건물명·호실·신청번호 부분 일치, 대소문자 무시)
    // pattern은 서비스가 소문자 + %...% + 특수문자(!, %, _) 이스케이프까지 해서 넘긴다
    // 제출 전 신청은 applicant_name이 NULL이라 coalesce로 빈 문자열 처리
    @Query("""
            select c from Contract c
             where c.status in :statuses
               and (lower(coalesce(c.applicantName, '')) like :pattern escape '!'
                    or lower(c.buildingName) like :pattern escape '!'
                    or lower(c.roomName) like :pattern escape '!'
                    or lower(c.applicationNo) like :pattern escape '!')
             order by c.createdAt desc
            """)
    List<Contract> searchForAdmin(@Param("statuses") Collection<ContractStatus> statuses,
                                  @Param("pattern") String pattern);

    // 입주 전 취소 요청: 결제 완료(PAID·COMPLETED) → MOVE_IN_CANCEL_REQUESTED. 두 번 눌러도 한 번만 1을 받는다
    // flushAutomatically: 같은 트랜잭션에서 먼저 바꾼 엔티티를 UPDATE 전에 DB에 반영 (clear로 사라지지 않게)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Contract c
               set c.status = com.coliving.platform.contract.enums.ContractStatus.MOVE_IN_CANCEL_REQUESTED,
                   c.updatedAt = :now,
                   c.version = c.version + 1
             where c.id = :id
               and c.status = :from
            """)
    int markMoveInCancelRequested(@Param("id") Long id, @Param("from") ContractStatus from, @Param("now") Instant now);

    // 환불 완료: MOVE_IN_CANCEL_REQUESTED → REFUNDED (no_overlap_booking 대상이 아니라서 같은 기간 예약이 바로 풀린다)
    // 입주 전 취소가 아닌 자동 환불(신청이 EXPIRED 등)이면 0 → 신청 상태는 건드리지 않는다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Contract c
               set c.status = com.coliving.platform.contract.enums.ContractStatus.REFUNDED,
                   c.updatedAt = :now,
                   c.version = c.version + 1
             where c.id = :id
               and c.status = com.coliving.platform.contract.enums.ContractStatus.MOVE_IN_CANCEL_REQUESTED
            """)
    int markRefundedIfCancelRequested(@Param("id") Long id, @Param("now") Instant now);

    // 만료 스케줄러: 기한(expiresAt)이 지난 신청 ID (오래된 것부터 한 번에 limit건)
    @Query("""
            select c.id from Contract c
             where c.status = :status
               and c.expiresAt < :now
             order by c.expiresAt
            """)
    List<Long> findExpiredIds(@Param("status") ContractStatus status, @Param("now") Instant now, Pageable limit);

    // 만료: from(DRAFT·APPROVED) → EXPIRED. 그 사이 제출·결제됐으면 0 (조건부 UPDATE라 결제와 겹쳐도 한쪽만 성공)
    // EXPIRED는 no_overlap_booking 대상이 아니라서 만료 즉시 그 기간에 다른 신청이 가능해진다
    @Modifying(clearAutomatically = true)
    @Query("""
            update Contract c
               set c.status = com.coliving.platform.contract.enums.ContractStatus.EXPIRED,
                   c.updatedAt = :now,
                   c.version = c.version + 1
             where c.id = :id
               and c.status = :from
               and c.expiresAt < :now
            """)
    int expireIfOverdue(@Param("id") Long id, @Param("from") ContractStatus from, @Param("now") Instant now);

    // 입주 처리 스케줄러: 입주일이 된(입주일 <= today) 결제 완료(PAID) 신청 ID
    @Query("""
            select c.id from Contract c
             where c.status = com.coliving.platform.contract.enums.ContractStatus.PAID
               and c.moveInDate <= :today
             order by c.moveInDate
            """)
    List<Long> findPaidToMoveIn(@Param("today") LocalDate today, Pageable limit);

    // 입주: PAID → COMPLETED. 그 사이 입주 전 취소 등으로 상태가 바뀌었으면 0
    @Modifying(clearAutomatically = true)
    @Query("""
            update Contract c
               set c.status = com.coliving.platform.contract.enums.ContractStatus.COMPLETED,
                   c.updatedAt = :now,
                   c.version = c.version + 1
             where c.id = :id
               and c.status = com.coliving.platform.contract.enums.ContractStatus.PAID
               and c.moveInDate <= :today
            """)
    int markCompletedIfMovedIn(@Param("id") Long id, @Param("today") LocalDate today, @Param("now") Instant now);

    @Query(value = "SELECT nextval('application_no_seq')", nativeQuery = true)
    long nextApplicationSeq();

    // APPROVED → PAID. 만료 처리·취소와 겹쳐도 한쪽만 성공하도록 조건부 UPDATE (0이면 이미 다른 상태)
    // 벌크 UPDATE는 @Version을 자동으로 올리지 않으므로 직접 올린다
    @Modifying(clearAutomatically = true)
    @Query("""
            update Contract c
               set c.status = com.coliving.platform.contract.enums.ContractStatus.PAID,
                   c.expiresAt = null,
                   c.updatedAt = :now,
                   c.version = c.version + 1
             where c.id = :id
               and c.status = com.coliving.platform.contract.enums.ContractStatus.APPROVED
            """)
    int markPaidIfApproved(@Param("id") Long id, @Param("now") Instant now);

    // 개인정보 파기 대상: 끝난 신청(REJECTED·CANCELED·EXPIRED) 중 마지막 처리 후 cutoff가 지났고,
    // 아직 신청자 정보나 계약서가 남아 있는 것 (파기 후에는 둘 다 없어서 다시 잡히지 않는다)
    @Query("""
            select c.id from Contract c
             where c.status in :statuses
               and c.updatedAt < :cutoff
               and (c.applicantName is not null
                    or c.applicantEmail is not null
                    or c.applicantPhone is not null
                    or c.applicantAddress is not null
                    or exists (select 1 from ContractDocument d where d.applicationId = c.id))
             order by c.updatedAt
            """)
    List<Long> findPurgeTargetIds(@Param("statuses") Collection<ContractStatus> statuses,
                                  @Param("cutoff") Instant cutoff, Pageable limit);

    // 신청자 정보 비우기. 행은 남겨서 신청번호·상태 이력·반려 사유로 계속 추적할 수 있게 한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Contract c
               set c.applicantName = null,
                   c.applicantEmail = null,
                   c.applicantPhone = null,
                   c.applicantAddress = null,
                   c.updatedAt = :now,
                   c.version = c.version + 1
             where c.id = :id
               and c.status in :statuses
            """)
    int clearApplicant(@Param("id") Long id, @Param("statuses") Collection<ContractStatus> statuses,
                       @Param("now") Instant now);

    // 1-1 호실 선택 "다른 방 추천": roomIds 중 [moveIn, moveOut] 기간에 예약이 걸친 호실 ID
    //   겹침 조건은 findBookedPeriods·화면 conflict 계산과 같다 (입주일 <= 상대 퇴실일 and 퇴실일 >= 상대 입주일)
    //   보는 사람 본인의 작성 중(DRAFT) 신청은 막힌 기간으로 보지 않는다. 비로그인은 -1을 넘긴다
    @Query("""
            select distinct c.roomId
              from Contract c
             where c.roomId in :roomIds
               and c.status in :statuses
               and c.moveInDate <= :moveOut
               and c.moveOutDate >= :moveIn
               and not (c.status = com.coliving.platform.contract.enums.ContractStatus.DRAFT
                        and c.userId = :excludeDraftUserId)
            """)
    List<Long> findBookedRoomIds(@Param("roomIds") Collection<Long> roomIds,
                                 @Param("statuses") Collection<ContractStatus> statuses,
                                 @Param("moveIn") LocalDate moveIn,
                                 @Param("moveOut") LocalDate moveOut,
                                 @Param("excludeDraftUserId") Long excludeDraftUserId);

}
