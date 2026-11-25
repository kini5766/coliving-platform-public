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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 입주 처리: 입주일이 된 결제 완료(PAID) 신청을 계약 완료(COMPLETED)로 바꾼다. (2026-11-05 결정: 완료 시점 = 입주일)
 *
 *   PAID      = 결제 끝, 입주 예정
 *   COMPLETED = 입주 (대시보드 호실 현황 OCCUPIED · 활동 로그 "입주 완료"와 같은 뜻)
 *
 * 입주 전 취소는 입주일 전날까지만 되므로(PaymentService.cancelBeforeMoveIn), 입주일이 된 계약은 취소와 겹치지 않는다.
 * 그래도 조건부 UPDATE(아직 PAID이고 입주일이 지났을 때만)라서 겹치면 한쪽만 성공한다.
 *
 * 자정 한 번이 아니라 1시간마다 돈다 → 자정에 서버가 꺼져 있었어도 다음 회차에 처리된다.
 *
 * 설정 (application.yaml, 없으면 아래 기본값)
 *   app.contract.move-in.interval   : 몇 분마다 돌지 (기본 PT1H)
 *   app.contract.move-in.batch-size : 한 번에 처리할 건수 (기본 200)
 *
 * @author 한성현
 */
@Component
public class MoveInScheduler {

    private static final Logger log = LoggerFactory.getLogger(MoveInScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ContractRepository contractRepository;
    private final HistoryRepository historyRepository;
    private final TransactionTemplate tx;
    private final ContractStatusEvents statusEvents;
    private final int batchSize;

    public MoveInScheduler(ContractRepository contractRepository,
                           HistoryRepository historyRepository,
                           TransactionTemplate tx,
                           ContractStatusEvents statusEvents,
                           @Value("${app.contract.move-in.batch-size:200}") int batchSize) {
        this.contractRepository = contractRepository;
        this.historyRepository = historyRepository;
        this.tx = tx;
        this.statusEvents = statusEvents;
        this.batchSize = batchSize;
    }

    // initialDelay: 서버가 뜨고 90초 뒤 첫 회차 (밀린 입주 처리를 바로 따라잡는다)
    @Scheduled(fixedDelayString = "${app.contract.move-in.interval:PT1H}", initialDelay = 90_000)
    public void completeMoveIns() {
        LocalDate today = LocalDate.now(SEOUL);
        Instant now = Instant.now();
        List<Long> ids = contractRepository.findPaidToMoveIn(today, PageRequest.of(0, batchSize));
        int done = 0;
        for (Long id : ids) {
            try {
                Boolean ok = tx.execute(status -> {
                    if (contractRepository.markCompletedIfMovedIn(id, today, now) == 0) return false;
                    statusEvents.publish(historyRepository.save(ContractStatusHistory.system(id, ContractStatus.PAID, ContractStatus.COMPLETED)));
                    return true;
                });
                if (Boolean.TRUE.equals(ok)) done++;
            } catch (RuntimeException e) {
                log.warn("입주 처리 실패 (다음 회차에 재시도) applicationId={}", id, e);
            }
        }
        if (done > 0) log.info("입주 처리: PAID → COMPLETED {}건 (기준일 {})", done, today);
    }
}
