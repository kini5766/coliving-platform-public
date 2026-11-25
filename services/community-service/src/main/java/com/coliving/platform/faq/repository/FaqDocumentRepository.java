package com.coliving.platform.faq.repository;

import com.coliving.platform.faq.entity.FaqDocumentEntity;
import com.coliving.platform.faq.entity.FaqIndexStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqDocumentRepository extends JpaRepository<FaqDocumentEntity, Long> {

	/** 7-2 목록 (원본 파일 제외, 최신 업로드순) — 문서 수가 많지 않아서 필터는 서비스에서 */
	@Query("select new com.coliving.platform.faq.repository.FaqDocumentRow(d.id, d.title, d.scope, d.buildingId, "
			+ "d.fileName, d.fileType, d.fileSize, d.indexStatus, d.indexError, d.indexedAt, d.createdAt) "
			+ "from FaqDocumentEntity d order by d.createdAt desc, d.id desc")
	List<FaqDocumentRow> findAllRows();

	/** 반영할 문서 ID (상태 기준) */
	@Query("select d.id from FaqDocumentEntity d where d.indexStatus in :statuses order by d.id")
	List<Long> findIdsByStatusIn(@Param("statuses") Collection<FaqIndexStatus> statuses);

	@Query("select d.id from FaqDocumentEntity d where d.id in :ids")
	List<Long> findExistingIds(@Param("ids") Collection<Long> ids);

	/** 서버 재시작으로 멈춘 문서 → 실패 처리 */
	@Modifying
	@Query("update FaqDocumentEntity d set d.indexStatus = :to, d.indexError = :error where d.indexStatus = :from")
	int updateStatus(@Param("from") FaqIndexStatus from, @Param("to") FaqIndexStatus to, @Param("error") String error);
}
