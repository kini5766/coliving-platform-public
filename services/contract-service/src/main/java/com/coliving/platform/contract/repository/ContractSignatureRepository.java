package com.coliving.platform.contract.repository;

import com.coliving.platform.contract.entity.ContractSignature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * @author 한성현
 */
public interface ContractSignatureRepository extends JpaRepository<ContractSignature, Long> {

    // (application_id, document_id) UNIQUE → 이 계약서에 서명했는지
    boolean existsByApplicationIdAndDocumentId(Long applicationId, Long documentId);

    Optional<ContractSignature> findByApplicationIdAndDocumentId(Long applicationId, Long documentId);

    // 개인정보 파기용: 서명 파일 경로를 먼저 읽고, 행을 지운다 (contract_document를 참조하므로 계약서보다 먼저)
    List<ContractSignature> findByApplicationId(Long applicationId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ContractSignature s where s.applicationId = :applicationId")
    int deleteByApplicationIdForPurge(@Param("applicationId") Long applicationId);
}
