package com.coliving.platform.contract.repository;

import com.coliving.platform.contract.dto.internal.MyContractRow;
import com.coliving.platform.contract.entity.ContractDocument;
import com.coliving.platform.contract.enums.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * @author 한성현
 */
public interface ContractDocumentRepository extends JpaRepository<ContractDocument, Long> {

    // 계약서 1건 (내 계약서 보기, 2단계 계약서 확인·서명)
    Optional<ContractDocument> findByApplicationId(Long applicationId);

    // 내 계약서 목록: 계약서가 있는 내 신청 중 hidden 상태(승인 전)를 뺀 것 (엔티티 간 연관관계가 없어서 ON 조건으로 직접 조인)
    // 본문(content)은 크므로 목록에서는 가져오지 않는다
    @Query("""
            select new com.coliving.platform.contract.dto.internal.MyContractRow(
                c.applicationNo, c.status, c.buildingName, c.roomName, c.roomType,
                c.moveInDate, c.moveOutDate, c.contractMonths,
                c.monthlyFee, c.deposit, c.firstPayment,
                d.id, d.language, d.createdAt)
            from ContractDocument d
            join Contract c on c.id = d.applicationId
            where c.userId = :userId
              and c.status not in :hidden
            order by d.createdAt desc
            """)
    List<MyContractRow> findMyContracts(@Param("userId") Long userId,
                                        @Param("hidden") Collection<ContractStatus> hidden);

    // 개인정보 파기용. DB 트리거가 신청 상태가 REJECTED·CANCELED·EXPIRED일 때만 허용한다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ContractDocument d where d.applicationId = :applicationId")
    int deleteByApplicationIdForPurge(@Param("applicationId") Long applicationId);
}
