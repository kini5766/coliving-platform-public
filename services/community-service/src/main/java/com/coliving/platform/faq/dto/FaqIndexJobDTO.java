package com.coliving.platform.faq.dto;

import com.coliving.platform.faq.entity.FaqIndexJobEntity;
import com.coliving.platform.faq.entity.FaqIndexStatus;
import java.time.LocalDateTime;

/** 7-6 응답 · 7-7 진행 상태 */
public record FaqIndexJobDTO(
		Long jobId,
		FaqIndexStatus status,
		int processedCount,
		int totalCount,
		String errorMessage,
		LocalDateTime startedAt,
		LocalDateTime finishedAt
) {

	public static FaqIndexJobDTO from(FaqIndexJobEntity job) {
		return new FaqIndexJobDTO(job.getId(), job.getStatus(), job.getProcessedCount(), job.getTotalCount(),
				job.getErrorMessage(), job.getCreatedAt(), job.getFinishedAt());
	}
}
