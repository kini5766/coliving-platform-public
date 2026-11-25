package com.coliving.platform.user.repository;

import com.coliving.platform.user.entity.ContractAutocompleteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 계약서 자동완성. 키는 회원 번호다.
 *
 * @author 조휘재
 * 작성일: 2026-11-01
 */
@Repository
public interface ContractAutocompleteRepository extends JpaRepository<ContractAutocompleteEntity, Long> {
}
