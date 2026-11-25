package com.coliving.platform.faq.repository;

import com.coliving.platform.faq.entity.FaqFileType;
import com.coliving.platform.faq.entity.FaqIndexStatus;
import com.coliving.platform.faq.entity.FaqScope;
import java.time.LocalDateTime;

/** 문서 목록용 — 원본 파일(file_data) 없이 필요한 칸만 */
public record FaqDocumentRow(
		Long id,
		String title,
		FaqScope scope,
		Long buildingId,
		String fileName,
		FaqFileType fileType,
		long fileSize,
		FaqIndexStatus indexStatus,
		String indexError,
		LocalDateTime indexedAt,
		LocalDateTime createdAt
) {
}
