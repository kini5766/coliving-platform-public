package com.coliving.platform.contract.service;

import com.coliving.platform.common.exception.ApplicationNotFoundException;
import com.coliving.platform.contract.dto.admin.ApplicationApproveResponse;
import com.coliving.platform.contract.dto.admin.ApplicationRejectResponse;
import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.entity.ContractDocument;
import com.coliving.platform.contract.entity.ContractStatusHistory;
import com.coliving.platform.contract.repository.ContractDocumentRepository;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.HistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.ZoneOffset;

import static com.coliving.platform.contract.enums.ContractStatus.APPROVED;
import static com.coliving.platform.contract.enums.ContractStatus.REJECTED;
import static com.coliving.platform.contract.enums.ContractStatus.SUBMITTED;

/**
 * 관리자 심사.
 *   - 승인: SUBMITTED → APPROVED + 결제 기한(+1일) 시작
 *   - 반려: SUBMITTED → REJECTED + 사유 저장 (같은 기간 예약이 바로 풀린다)
 *
 * 계약서(contract_document)는 신청서 제출 때 만들어진다 (ApplicationService.submit, 2026-11-05 변경).
 *   → 관리자는 심사 화면에서 계약서 본문을 보고 승인·반려한다. 승인은 상태만 바꾼다.
 *
 * 동시에 두 관리자가 승인·반려하면?
 *   - contract_application.version(@Version) → 늦은 쪽 UPDATE 실패 → 409 (CustomControllerAdvice)
 *   - 엔티티가 SUBMITTED인지 확인 → 이미 처리됐으면 IllegalStateException → 409
 *
 * @Transactional 대신 TransactionTemplate을 쓰는 이유:
 *   같은 클래스 안에서 @Transactional 메서드를 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않는다.
 *   트랜잭션 경계를 코드에서 눈으로 확인할 수 있게 명시적으로 묶는다.
 *
 * @author 한성현
 */
@Service
public class ApplicationApprovalService {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final int REJECT_REASON_MAX = 500;               // reject_reason VARCHAR(500)

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository documentRepository;
    private final HistoryRepository historyRepository;
    private final TransactionTemplate tx;
    private final ContractStatusEvents statusEvents;

    public ApplicationApprovalService(ContractRepository contractRepository,
                                      ContractDocumentRepository documentRepository,
                                      HistoryRepository historyRepository,
                                      TransactionTemplate tx,
                                      ContractStatusEvents statusEvents) {
        this.contractRepository = contractRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.tx = tx;
        this.statusEvents = statusEvents;
    }

    // =====================================================================
    // 승인: SUBMITTED → APPROVED. 계약서는 제출 때 이미 만들어져 있다
    // =====================================================================
    public ApplicationApproveResponse approve(Long adminId, Long applicationId) {
        return tx.execute(status -> {
            Contract c = contractRepository.findById(applicationId)
                    .orElseThrow(() -> new ApplicationNotFoundException("신청서를 찾을 수 없습니다."));
            // 제출 때 계약서가 만들어지므로 없으면 비정상 데이터 (변경 전에 제출된 신청 등) → 409
            ContractDocument doc = documentRepository.findByApplicationId(c.getId())
                    .orElseThrow(() -> new IllegalStateException("계약서가 없는 신청입니다. 신청자가 다시 신청해야 합니다."));

            c.approve(Instant.now());                                   // SUBMITTED 확인 + 결제 기한(+1일) 시작
            statusEvents.publish(historyRepository.save(ContractStatusHistory.admin(c.getId(), SUBMITTED, APPROVED, adminId, null)));

            contractRepository.flush();                                 // @Version 충돌을 여기서 바로 받는다

            return new ApplicationApproveResponse(
                    c.getApplicationNo(),
                    c.getStatus(),
                    doc.getId(),
                    doc.getContentHash(),
                    c.getExpiresAt().atOffset(KST)
            );
        });
    }

    // =====================================================================
    // 반려: SUBMITTED → REJECTED (최종 상태. 재신청은 새 신청으로)
    // - no_overlap_booking 제약은 REJECTED를 제외하므로, 반려 즉시 같은 기간에 다른 신청이 가능해진다
    // - 동시에 다른 관리자가 승인했다면 엔티티의 상태 확인 또는 @Version 충돌로 409
    // =====================================================================
    public ApplicationRejectResponse reject(Long adminId, Long applicationId, String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw new IllegalArgumentException("반려 사유를 입력해주세요.");
        }
        if (r.length() > REJECT_REASON_MAX) {
            throw new IllegalArgumentException("반려 사유는 " + REJECT_REASON_MAX + "자 이내로 입력해주세요.");
        }

        return tx.execute(status -> {
            Contract c = contractRepository.findById(applicationId)
                    .orElseThrow(() -> new ApplicationNotFoundException("신청서를 찾을 수 없습니다."));
            c.reject(r);                                                // SUBMITTED 확인은 엔티티가 한다
            statusEvents.publish(historyRepository.save(ContractStatusHistory.admin(c.getId(), SUBMITTED, REJECTED, adminId, r)));

            contractRepository.flush();                                 // @Version 충돌을 여기서 바로 받는다

            return new ApplicationRejectResponse(
                    c.getApplicationNo(),
                    c.getStatus(),
                    c.getRejectReason(),
                    Instant.now().atOffset(KST)
            );
        });
    }
}
