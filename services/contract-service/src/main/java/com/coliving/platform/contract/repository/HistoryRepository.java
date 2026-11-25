package com.coliving.platform.contract.repository;

import com.coliving.platform.contract.entity.ContractStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author 한성현
 */
public interface HistoryRepository extends JpaRepository<ContractStatusHistory,Long> {
}
