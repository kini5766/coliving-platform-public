package com.coliving.platform.contract.scheduler;

import com.coliving.platform.contract.entity.ContractStatusHistory;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.repository.ContractRepository;
import com.coliving.platform.contract.repository.HistoryRepository;
import com.coliving.platform.contract.service.ContractStatusEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * 기한이 지난 신청서를 EXPIRED로 바꾼다.
 *   - 작성 중(DRAFT): 만든 지 2일 지남 ("다음 단계"만 누르고 나간 신청서)
 *   - 승인·결제 대기(APPROVED): 승인 후 1일 안에 결제하지 않음
 *
 * 왜 필요한가: DRAFT·APPROVED는 DB 제약 no_overlap_booking 대상이라, 그대로 두면 버려진 신청서가
 *   그 호실 기간을 계속 막는다 (신청 화면 예약 현황·매물 입주 가능 여부에도 "예약됨"으로 나옴).
 *   EXPIRED는 대상이 아니라서 바뀌는 즉시 기간이 풀린다.
 *
 * 규칙
 *   - 건마다 짧은 트랜잭션: 조건부 UPDATE(아직 그 상태이고 기한이 지났을 때만) + 상태 이력(SYSTEM)
 *   - 그 사이 사용자가 제출·결제했으면 UPDATE가 0건 → 건너뛴다
 *   - 결제창에 있던 사람이 만료 뒤 결제를 마치면 PaymentService가 자동 환불한다 (신청 상태가 APPROVED가 아니므로)
 *   - 한 건이 실패해도 다음 건은 계속. 실패한 건은 상태가 그대로라 다음 회차에 다시 대상이 된다
 *
 * 설정 (application.yaml, 없으면 아래 기본값)
 *   app.contract.expiry.interval   : 몇 분마다 돌지 (기본 PT5M)
 *   app.contract.expiry.batch-size : 상태별 한 번에 처리할 건수 (기본 200)
 *
 * ⚠ 서버를 2대 이상 띄워도 조건부 UPDATE라 한 건이 두 번 만료되지는 않는다 (이력도 UPDATE 1건일 때만 남김).
 *
 * @author 한성현
 */
@Component
public class ContractExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContractExpiryScheduler.class);
    private static final List<ContractStatus> TARGETS = List.of(ContractStatus.DRAFT, ContractStatus.APPROVED);

    private final ContractRepository contractRepository;
    private final HistoryRepository historyRepository;
    private final TransactionTemplate tx;
    private final ContractStatusEvents statusEvents;
    private final int batchSize;

    public ContractExpiryScheduler(ContractRepository contractRepository,
                                   HistoryRepository historyRepository,
                                   TransactionTemplate tx,
                                   ContractStatusEvents statusEvents,
                                   @Value("${app.contract.expiry.batch-size:200}") int batchSize) {
        this.contractRepository = contractRepository;
        this.historyRepository = historyRepository;
        this.tx = tx;
        this.statusEvents = statusEvents;
        this.batchSize = batchSize;
    }

    // fixedDelay: 앞 회차가 끝나고 interval 뒤에 다음 회차 / initialDelay: 서버가 뜨고 1분 뒤부터
    @Scheduled(fixedDelayString = "${app.contract.expiry.interval:PT5M}", initialDelay = 60_000)
    public void expireOverdue() {
        Instant now = Instant.now();
        for (ContractStatus from : TARGETS) {
            List<Long> ids = contractRepository.findExpiredIds(from, now, PageRequest.of(0, batchSize));
            int expired = 0;
            for (Long id : ids) {
                try {
                    if (expire(id, from, now)) expired++;
                } catch (RuntimeException e) {
                    log.warn("신청서 만료 실패 (다음 회차에 재시도) applicationId={}, from={}", id, from, e);
                }
            }
            if (expired > 0) log.info("기한 지난 신청서 만료: {} → EXPIRED {}건", from, expired);
        }
    }

    // 한 건: 조건부 UPDATE + 이력. 그 사이 상태가 바뀌었으면 false
    private boolean expire(Long id, ContractStatus from, Instant now) {
        Boolean done = tx.execute(status -> {
            if (contractRepository.expireIfOverdue(id, from, now) == 0) return false;
            statusEvents.publish(historyRepository.save(ContractStatusHistory.system(id, from, ContractStatus.EXPIRED)));
            return true;
        });
        return Boolean.TRUE.equals(done);
    }
}
