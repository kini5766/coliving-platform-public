package com.coliving.platform.faq.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 7-8 질문 기록 (PageResponse 형식 + summary) */
public record FaqQuestionLogListResponseDTO(
		Summary summary,
		List<Item> content,
		int page,
		int size,
		long totalElements,
		int totalPages
) {

	public record Summary(long totalCount, long unansweredCount, long cachedCount, int avgLatencyMs) {
	}

	public record Item(
			Long logId,
			String question,
			Long buildingId,
			String buildingName,
			boolean answered,
			Float topScore,
			List<FaqSourceDTO> sources,
			boolean cached,
			Integer latencyMs,
			LocalDateTime createdAt
	) {
	}
}
