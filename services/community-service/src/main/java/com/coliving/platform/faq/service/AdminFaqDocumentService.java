package com.coliving.platform.faq.service;

import com.coliving.platform.client.PropertyClient;
import com.coliving.platform.common.exception.CommunityException;
import com.coliving.platform.common.exception.ErrorCode;
import com.coliving.platform.faq.dto.FaqDocumentListResponseDTO;
import com.coliving.platform.faq.dto.FaqDocumentUploadResponseDTO;
import com.coliving.platform.faq.dto.FaqIndexJobDTO;
import com.coliving.platform.faq.entity.FaqDocumentEntity;
import com.coliving.platform.faq.entity.FaqFileType;
import com.coliving.platform.faq.entity.FaqIndexJobEntity;
import com.coliving.platform.faq.entity.FaqIndexStatus;
import com.coliving.platform.faq.entity.FaqScope;
import com.coliving.platform.faq.repository.FaqDocumentRepository;
import com.coliving.platform.faq.repository.FaqDocumentRow;
import com.coliving.platform.faq.repository.FaqIndexJobRepository;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * FAQ 문서 관리 (API 7-2 ~ 7-7)
 * 업로드만 하면 PENDING — "업데이트 반영"(7-6)을 눌러야 봇에 적용됨
 * TODO(연동 1-3): 관리자 담당 건물만 보이게
 */
@Service
@RequiredArgsConstructor
public class AdminFaqDocumentService {

	private static final long MAX_FILE_SIZE = 30L * 1024 * 1024;
	private static final Set<FaqIndexStatus> NEEDS_INDEX = Set.of(FaqIndexStatus.PENDING, FaqIndexStatus.FAILED);

	private final FaqDocumentRepository faqDocumentRepository;
	private final FaqIndexJobRepository faqIndexJobRepository;
	private final FaqVectorService faqVectorService;
	private final PropertyClient propertyClient;
	private final ApplicationEventPublisher eventPublisher;
	private final FaqAnswerCache answerCache;

	/** 7-2 문서 목록 · 반영 상태 */
	@Transactional(readOnly = true)
	public FaqDocumentListResponseDTO getDocuments(FaqScope scope, Long buildingId) {
		List<FaqDocumentRow> rows = faqDocumentRepository.findAllRows();

		FaqDocumentListResponseDTO.Summary summary = new FaqDocumentListResponseDTO.Summary(
				rows.stream().map(FaqDocumentRow::indexedAt).filter(Objects::nonNull).max(Comparator.naturalOrder())
						.orElse(null),
				rows.stream().filter(row -> row.indexStatus() == FaqIndexStatus.COMPLETED).count(),
				rows.stream().filter(row -> NEEDS_INDEX.contains(row.indexStatus())).count());

		Map<Long, String> buildingNames = propertyClient.getBuildings().stream()
				.collect(Collectors.toMap(PropertyClient.BuildingSummary::buildingId,
						PropertyClient.BuildingSummary::name, (a, b) -> a));
		List<FaqDocumentListResponseDTO.Item> documents = rows.stream()
				.filter(row -> scope == null || row.scope() == scope)
				.filter(row -> buildingId == null || buildingId.equals(row.buildingId()))
				.map(row -> new FaqDocumentListResponseDTO.Item(row.id(), row.title(), row.scope(), row.buildingId(),
						row.buildingId() != null ? buildingNames.get(row.buildingId()) : null,
						row.fileName(), row.fileType(), row.fileSize(), row.createdAt(), row.indexStatus(),
						row.indexedAt(), row.indexError()))
				.toList();
		return new FaqDocumentListResponseDTO(summary, documents);
	}

	/** 7-3 업로드 — 원본을 DB에 저장하고 PENDING */
	@Transactional
	public FaqDocumentUploadResponseDTO upload(Long adminId, MultipartFile file, String title, FaqScope scope,
			Long buildingId) {
		if (file == null || file.isEmpty()) {
			throw new CommunityException(ErrorCode.INVALID_REQUEST, "파일을 선택해 주세요.");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new CommunityException(ErrorCode.FILE_TOO_LARGE);
		}
		String fileName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename().strip();
		FaqFileType fileType = FaqFileType.fromFileName(fileName);
		if (fileType == null) {
			throw new CommunityException(ErrorCode.INVALID_REQUEST, "PDF·DOCX·TXT·MD 파일만 올릴 수 있어요.");
		}
		String trimmedTitle = title == null ? "" : title.strip();
		if (trimmedTitle.isEmpty() || trimmedTitle.length() > 100) {
			throw new CommunityException(ErrorCode.INVALID_REQUEST, "문서 제목은 1~100자로 입력해 주세요.");
		}
		if (scope == FaqScope.BUILDING) {
			if (buildingId == null) {
				throw new CommunityException(ErrorCode.INVALID_REQUEST, "건물 문서는 건물을 선택해 주세요.");
			}
			if (propertyClient.getBuildingName(buildingId) == null) {
				throw new CommunityException(ErrorCode.INVALID_REQUEST, "없는 건물입니다.");
			}
		}

		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException e) {
			throw new CommunityException(ErrorCode.INVALID_REQUEST, "파일을 읽을 수 없어요. 다시 올려 주세요.");
		}
		FaqDocumentEntity saved = faqDocumentRepository.save(
				FaqDocumentEntity.create(trimmedTitle, scope, buildingId, fileName, fileType, bytes, adminId));
		return new FaqDocumentUploadResponseDTO(saved.getId(), saved.getIndexStatus(), saved.getCreatedAt());
	}

	/** 7-4 삭제 — 원본과 문서 조각(임베딩) 함께 */
	@Transactional
	public void delete(Long documentId) {
		FaqDocumentEntity document = findDocument(documentId);
		faqVectorService.deleteChunks(documentId);
		faqDocumentRepository.delete(document);
		answerCache.bumpVersion();   // 삭제된 문서로 만든 답변 캐시는 안 쓰기
	}

	/** 7-5 원본 다운로드 */
	@Transactional(readOnly = true)
	public FaqDocumentEntity getFile(Long documentId) {
		return findDocument(documentId);
	}

	/** 7-6 반영 시작 — 작업만 만들고 바로 응답, 실제 반영은 커밋 후 FaqIndexWorker 가 비동기로 */
	@Transactional
	public FaqIndexJobDTO startReindex(Long adminId, List<Long> documentIds) {
		if (faqIndexJobRepository.existsByStatus(FaqIndexStatus.PROCESSING)) {
			throw new CommunityException(ErrorCode.REINDEX_IN_PROGRESS);
		}
		List<Long> targets;
		if (documentIds == null || documentIds.isEmpty()) {
			targets = faqDocumentRepository.findIdsByStatusIn(NEEDS_INDEX);
		} else {
			targets = List.copyOf(new LinkedHashSet<>(documentIds));
			Set<Long> existing = Set.copyOf(faqDocumentRepository.findExistingIds(targets));
			if (!existing.containsAll(targets)) {
				throw new CommunityException(ErrorCode.DOCUMENT_NOT_FOUND);
			}
		}
		if (targets.isEmpty()) {
			throw new CommunityException(ErrorCode.INVALID_REQUEST, "반영할 문서가 없어요. (대기·실패 문서 없음)");
		}

		FaqIndexJobEntity job = faqIndexJobRepository.save(FaqIndexJobEntity.start(adminId, targets.size()));
		eventPublisher.publishEvent(new FaqReindexRequestedEvent(job.getId(), targets));
		return FaqIndexJobDTO.from(job);
	}

	/** 7-7 반영 진행 상태 */
	@Transactional(readOnly = true)
	public FaqIndexJobDTO getJob(Long jobId) {
		return faqIndexJobRepository.findById(jobId)
				.map(FaqIndexJobDTO::from)
				.orElseThrow(() -> new CommunityException(ErrorCode.INVALID_REQUEST, "반영 작업을 찾을 수 없어요."));
	}

	/** 근거 문서 제목 (7-8, 7-1) — 삭제된 문서는 "삭제된 문서" */
	@Transactional(readOnly = true)
	public Map<Long, String> getTitles(List<Long> documentIds) {
		if (documentIds.isEmpty()) {
			return Map.of();
		}
		return faqDocumentRepository.findAllRows().stream()
				.filter(row -> documentIds.contains(row.id()))
				.collect(Collectors.toMap(FaqDocumentRow::id, FaqDocumentRow::title, (a, b) -> a));
	}

	private FaqDocumentEntity findDocument(Long documentId) {
		return faqDocumentRepository.findById(documentId)
				.orElseThrow(() -> new CommunityException(ErrorCode.DOCUMENT_NOT_FOUND));
	}

}
