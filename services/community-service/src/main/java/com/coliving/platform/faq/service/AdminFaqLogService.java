package com.coliving.platform.faq.service;

import com.coliving.platform.client.PropertyClient;
import com.coliving.platform.common.exception.CommunityException;
import com.coliving.platform.common.exception.ErrorCode;
import com.coliving.platform.faq.dto.FaqQuestionLogListResponseDTO;
import com.coliving.platform.faq.dto.FaqSourceDTO;
import com.coliving.platform.faq.entity.FaqQuestionLogEntity;
import com.coliving.platform.faq.repository.FaqQuestionLogRepository;
import com.coliving.platform.faq.repository.FaqQuestionLogSummaryRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 7-8 질문 기록 — 봇이 답하지 못한 질문을 보고 문서를 보완 */
@Service
@RequiredArgsConstructor
public class AdminFaqLogService {

	private static final int DEFAULT_DAYS = 7;

	private final FaqQuestionLogRepository faqQuestionLogRepository;
	private final AdminFaqDocumentService adminFaqDocumentService;
	private final PropertyClient propertyClient;

	@Transactional(readOnly = true)
	public FaqQuestionLogListResponseDTO getLogs(Boolean answered, Long buildingId, LocalDate from, LocalDate to,
			int page, int size) {
		LocalDate end = to != null ? to : LocalDate.now();
		LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
		if (start.isAfter(end)) {
			throw new CommunityException(ErrorCode.INVALID_REQUEST, "시작일이 종료일보다 늦어요.");
		}
		var fromTime = start.atStartOfDay();
		var toTime = end.plusDays(1).atStartOfDay();   // 종료일 하루 전체 포함

		FaqQuestionLogSummaryRow row = faqQuestionLogRepository.summarize(fromTime, toTime, buildingId);
		FaqQuestionLogListResponseDTO.Summary summary = new FaqQuestionLogListResponseDTO.Summary(
				nz(row.totalCount()), nz(row.unansweredCount()), nz(row.cachedCount()),
				row.avgLatencyMs() == null ? 0 : (int) Math.round(row.avgLatencyMs()));

		Page<FaqQuestionLogEntity> result = faqQuestionLogRepository.search(fromTime, toTime, buildingId, answered,
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));

		List<Long> docIds = result.getContent().stream()
				.flatMap(log -> log.getSourceDocIdList().stream()).distinct().toList();
		Map<Long, String> titles = adminFaqDocumentService.getTitles(docIds);
		Map<Long, String> buildingNames = propertyClient.getBuildings().stream()
				.collect(Collectors.toMap(PropertyClient.BuildingSummary::buildingId,
						PropertyClient.BuildingSummary::name, (a, b) -> a));

		List<FaqQuestionLogListResponseDTO.Item> content = result.getContent().stream()
				.map(log -> new FaqQuestionLogListResponseDTO.Item(log.getId(), log.getQuestion(), log.getBuildingId(),
						log.getBuildingId() != null ? buildingNames.get(log.getBuildingId()) : null,
						log.isAnswered(), log.getTopScore(),
						log.getSourceDocIdList().stream()
								.map(id -> new FaqSourceDTO(id, titles.getOrDefault(id, "삭제된 문서")))
								.toList(),
						log.isCached(), log.getLatencyMs(), log.getCreatedAt()))
				.toList();
		return new FaqQuestionLogListResponseDTO(summary, content, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	private long nz(Long value) {
		return value == null ? 0 : value;
	}
}
