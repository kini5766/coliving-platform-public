package com.coliving.platform.contract.repository;

import com.coliving.platform.contract.entity.OutboxEvent;
import com.coliving.platform.contract.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * @author 한성현
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // 발행 대기 이벤트를 오래된 것부터 (idx_outbox_pending 인덱스 사용)
    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxStatus status, Pageable limit);
}
