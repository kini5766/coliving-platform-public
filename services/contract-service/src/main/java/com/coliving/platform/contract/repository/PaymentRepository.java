package com.coliving.platform.contract.repository;

import com.coliving.platform.contract.entity.Payment;
import com.coliving.platform.contract.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @author 한성현
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    // 계약일(= 결제 완료일)용: 승인된 적 있는 결제 중 가장 먼저 승인된 것 (이후 환불돼도 approvedAt은 남는다)
    Optional<Payment> findFirstByApplicationIdAndApprovedAtIsNotNullOrderByApprovedAtAsc(Long applicationId);

    // 취소 영수증용: 승인된 적 있고 환불까지 끝난 결제 (중복 결제 자동 환불분은 approvedAt이 없어서 빠진다)
    Optional<Payment> findFirstByApplicationIdAndStatusAndApprovedAtIsNotNullOrderByApprovedAtAsc(
            Long applicationId, PaymentStatus status);

    // 신청의 성공한 결제 (uq_payment_one_paid → 최대 1건)
    Optional<Payment> findByApplicationIdAndStatus(Long applicationId, PaymentStatus status);

    // 보정 대상: 만든 지 before보다 오래된 PENDING (idx_payment_pending 인덱스 사용). 오래된 것부터 한 번에 limit건
    @Query("""
            select p from Payment p
             where p.status = com.coliving.platform.contract.enums.PaymentStatus.PENDING
               and p.createdAt < :before
             order by p.createdAt
            """)
    List<Payment> findPendingBefore(@Param("before") Instant before, Pageable limit);

    // 환불 재시도 대상: 마지막 변경이 before보다 오래된 CANCEL_REQUESTED (방금 취소 중인 건은 건드리지 않게)
    @Query("""
            select p from Payment p
             where p.status = com.coliving.platform.contract.enums.PaymentStatus.CANCEL_REQUESTED
               and p.updatedAt < :before
             order by p.updatedAt
            """)
    List<Payment> findCancelRequestedBefore(@Param("before") Instant before, Pageable limit);

    // PENDING → PAID. 같은 결제의 승인이 겹쳐도 한쪽만 1을 받는다
    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
               set p.status = com.coliving.platform.contract.enums.PaymentStatus.PAID,
                   p.paymentMethod = :method,
                   p.approvedAt = :approvedAt,
                   p.updatedAt = :now
             where p.id = :id
               and p.status = com.coliving.platform.contract.enums.PaymentStatus.PENDING
            """)
    int markPaidIfPending(@Param("id") Long id, @Param("method") String method,
                          @Param("approvedAt") Instant approvedAt, @Param("now") Instant now);

    // 승인됐지만 우리 쪽에 반영하지 못한 결제의 자동 환불이 실패했을 때: PENDING·FAILED·CANCELED → CANCEL_REQUESTED
    // → 환불 재시도 스케줄러(retryRefunds)가 다시 환불한다. PAID·이미 취소 중·환불 완료는 건드리지 않는다
    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
               set p.status = com.coliving.platform.contract.enums.PaymentStatus.CANCEL_REQUESTED,
                   p.failReason = :reason,
                   p.updatedAt = :now
             where p.id = :id
               and p.status in (com.coliving.platform.contract.enums.PaymentStatus.PENDING,
                                com.coliving.platform.contract.enums.PaymentStatus.FAILED,
                                com.coliving.platform.contract.enums.PaymentStatus.CANCELED)
            """)
    int markRefundPending(@Param("id") Long id, @Param("reason") String reason, @Param("now") Instant now);

    // PENDING → FAILED / CANCELED. 엔티티 저장은 전체 컬럼을 덮어쓰므로, 승인과 겹쳐도 PAID를 되돌리지 않게 조건부로 한다
    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
               set p.status = :status,
                   p.failReason = :reason,
                   p.updatedAt = :now
             where p.id = :id
               and p.status = com.coliving.platform.contract.enums.PaymentStatus.PENDING
            """)
    int closeIfPending(@Param("id") Long id, @Param("status") PaymentStatus status,
                       @Param("reason") String reason, @Param("now") Instant now);
}
