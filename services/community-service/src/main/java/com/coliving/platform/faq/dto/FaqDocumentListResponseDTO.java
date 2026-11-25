package com.coliving.platform.faq.dto;

import com.coliving.platform.faq.entity.FaqFileType;
import com.coliving.platform.faq.entity.FaqIndexStatus;
import com.coliving.platform.faq.entity.FaqScope;
import java.time.LocalDateTime;
import java.util.List;

/** 7-2 문서 목록 · 반영 상태 */
public record FaqDocumentListResponseDTO(Summary summary, List<Item> documents) {

	/** lastIndexedAt 마지막 반영 일시 / indexedCount 반영 완료 문서 수 / pendingCount 대기·실패 문서 수 */
	public record Summary(LocalDateTime lastIndexedAt, long indexedCount, long pendingCount) {
	}

	public record Item(
			Long documentId,
			String title,
			FaqScope scope,
			Long buildingId,
			String buildingName,
			String fileName,
			FaqFileType fileType,
			long fileSize,
			LocalDateTime uploadedAt,
			FaqIndexStatus indexStatus,
			LocalDateTime indexedAt,
			String indexError
	) {
	}
}
