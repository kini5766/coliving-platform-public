package com.coliving.platform.contract.scheduler;

import com.coliving.platform.contract.entity.ContractSignature;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractDocumentRepository;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.ContractSignatureRepository;
import com.coliving.platform.contract.service.ContractFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 끝난 신청(반려·취소·만료)의 개인정보를 보관 기간(기본 30일)이 지나면 파기한다.
 * 근거: 개인정보 수집·이용 동의서 4항 "신청이 반려·취소되거나 결제 기한이 지나 자동 취소된 경우: 처리 완료 후 30일 이내 파기"
 *
 * 지우는 것
 *   - contract_signature 행 + 서명 이미지·서명 PDF 파일 (인증 이름, 서명 이미지)
 *   - contract_document 행 (본문에 이름·전화·이메일·주소가 들어 있다. DB 트리거가 이 세 상태일 때만 DELETE 허용)
 *   - contract_application의 applicant_name/email/phone/address → NULL
 *
 * 남기는 것 (신청번호로 추적용)
 *   - contract_application 행: 신청번호, 상태, 호실·금액 스냅샷, reject_reason, user_id
 *   - contract_status_history 전부 (개인정보 없음: 상태, 처리자 ID, 사유, 시각)
 *   - payment 행 (결제 기록은 전자상거래법상 5년 보존. 이름 등은 없다)
 *
 * 규칙
 *   - "처리 완료 시각" = updated_at. 세 상태는 최종 상태라 이후 바뀌지 않는다
 *   - 건마다 짧은 트랜잭션. 파일 삭제는 없는 파일이면 무시하므로, 커밋 전에 실패해도 다음 회차에 그대로 다시 처리된다
 *   - 한 건이 실패해도 다음 건은 계속
 *
 * 설정 (application.yaml, 없으면 아래 기본값)
 *   app.contract.purge.retention  : 보관 기간 (기본 P30D)
 *   app.contract.purge.interval   : 몇 시간마다 돌지 (기본 PT1H)
 *   app.contract.purge.batch-size : 한 번에 처리할 건수 (기본 200)
 *
 * @author 한성현
 */
@Component
public class PersonalDataPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataPurgeScheduler.class);
    // DB 트리거 forbid_contract_document_change()의 허용 목록과 반드시 같게 유지할 것
    static final Set<ContractStatus> TARGETS =
            EnumSet.of(ContractStatus.REJECTED, ContractStatus.CANCELED, ContractStatus.EXPIRED);

    private final ContractRepository contractRepository;
    private final ContractDocumentRepository documentRepository;
    private final ContractSignatureRepository signatureRepository;
    private final ContractFileStorage fileStorage;
    private final TransactionTemplate tx;
    private final Duration retention;
    private final int batchSize;

    public PersonalDataPurgeScheduler(ContractRepository contractRepository,
                                      ContractDocumentRepository documentRepository,
                                      ContractSignatureRepository signatureRepository,
                                      ContractFileStorage fileStorage,
                                      TransactionTemplate tx,
                                      @Value("${app.contract.purge.retention:P30D}") Duration retention,
                                      @Value("${app.contract.purge.batch-size:200}") int batchSize) {
        this.contractRepository = contractRepository;
        this.documentRepository = documentRepository;
        this.signatureRepository = signatureRepository;
        this.fileStorage = fileStorage;
        this.tx = tx;
        this.retention = retention;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.contract.purge.interval:PT1H}", initialDelay = 120_000)
    public void purgeExpiredPersonalData() {
        Instant now = Instant.now();
        List<Long> ids = contractRepository.findPurgeTargetIds(TARGETS, now.minus(retention),
                PageRequest.of(0, batchSize));
        int purged = 0;
        for (Long id : ids) {
            try {
                purge(id, now);
                purged++;
            } catch (RuntimeException e) {
                log.warn("개인정보 파기 실패 (다음 회차에 재시도) applicationId={}", id, e);
            }
        }
        if (purged > 0) log.info("보관 기간 지난 신청 개인정보 파기: {}건", purged);
    }

    // 한 건: 서명(파일 → 행) → 계약서 → 신청자 정보 순서 (FK: signature → document → application)
    private void purge(Long id, Instant now) {
        tx.executeWithoutResult(status -> {
            for (ContractSignature s : signatureRepository.findByApplicationId(id)) {
                if (s.getSignatureImagePath() != null) fileStorage.delete(s.getSignatureImagePath());
                if (s.getSignedPdfPath() != null) fileStorage.delete(s.getSignedPdfPath());
            }
            signatureRepository.deleteByApplicationIdForPurge(id);
            documentRepository.deleteByApplicationIdForPurge(id);
            contractRepository.clearApplicant(id, TARGETS, now);
        });
    }
}
