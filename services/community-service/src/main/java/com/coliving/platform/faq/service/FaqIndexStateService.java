package com.coliving.platform.faq.service;

import com.coliving.platform.faq.entity.FaqDocumentEntity;
import com.coliving.platform.faq.entity.FaqFileType;
import com.coliving.platform.faq.entity.FaqIndexStatus;
import com.coliving.platform.faq.entity.FaqScope;
import com.coliving.platform.faq.repository.FaqDocumentRepository;
import com.coliving.platform.faq.repository.FaqIndexJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 반영 작업 중 상태 저장 — 단계마다 바로 커밋 (화면에서 진행 상태를 2~3초마다 조회하므로)
 * FaqIndexWorker 가 다른 스레드에서 호출
 */
@Service
@RequiredArgsConstructor
public class FaqIndexStateService {

	private final FaqDocumentRepository faqDocumentRepository;
	private final FaqIndexJobRepository faqIndexJobRepository;

	/** PROCESSING 으로 바꾸고 원본 파일 꺼내기 — 그 사이 삭제됐으면 null */
	@Transactional
	public DocumentSource startDocument(Long documentId) {
		return faqDocumentRepository.findById(documentId)
				.map(document -> {
					document.markProcessing();
					return DocumentSource.from(document);
				})
				.orElse(null);
	}

	@Transactional
	public void completeDocument(Long documentId) {
		faqDocumentRepository.findById(documentId).ifPresent(FaqDocumentEntity::markCompleted);
	}

	@Transactional
	public void failDocument(Long documentId, String error) {
		faqDocumentRepository.findById(documentId).ifPresent(document -> document.markFailed(error));
	}

	@Transactional
	public void progressJob(Long jobId) {
		faqIndexJobRepository.findById(jobId).ifPresent(job -> job.progress());
	}

	@Transactional
	public void finishJob(Long jobId, int failedCount) {
		faqIndexJobRepository.findById(jobId).ifPresent(job -> job.finish(failedCount));
	}

	/** 서버 재시작 때 진행 중이던 작업·문서 정리 */
	@Transactional
	public int abortRunning(String reason) {
		var running = faqIndexJobRepository.findByStatus(FaqIndexStatus.PROCESSING);
		running.forEach(job -> job.abort(reason));
		faqDocumentRepository.updateStatus(FaqIndexStatus.PROCESSING, FaqIndexStatus.FAILED, reason);
		return running.size();
	}

	/** 반영에 필요한 값만 (엔티티를 다른 스레드로 넘기지 않기 위해) */
	public record DocumentSource(Long id, String title, FaqScope scope, Long buildingId, String fileName,
			FaqFileType fileType, byte[] fileData) {

		static DocumentSource from(FaqDocumentEntity document) {
			return new DocumentSource(document.getId(), document.getTitle(), document.getScope(),
					document.getBuildingId(), document.getFileName(), document.getFileType(), document.getFileData());
		}
	}
}
