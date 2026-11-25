package com.coliving.platform.faq.service;

import com.coliving.platform.faq.entity.FaqScope;
import com.coliving.platform.faq.service.FaqIndexStateService.DocumentSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * FAQ 문서 반영 (비동기) — 7-6 이 작업을 만들고 바로 202 응답 → 커밋 후 이 클래스가 다른 스레드에서 처리
 * 문서마다: 텍스트 추출 → 조각 나누기 → 기존 조각 삭제 → Gemini 임베딩 + pgvector 저장
 * 무료 키 호출 제한 대비: 조각을 batchSize 개씩 나눠 보내고, 실패하면 잠깐 쉬었다가 다시 시도
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqIndexWorker {

	private static final List<Duration> RETRY_WAITS = List.of(Duration.ofSeconds(15), Duration.ofSeconds(30),
			Duration.ofSeconds(60));

	private final FaqIndexStateService stateService;
	private final FaqTextExtractor textExtractor;
	private final FaqTextSplitter textSplitter;
	private final FaqVectorService vectorService;
	private final VectorStore vectorStore;
	private final FaqAnswerCache answerCache;

	@Value("${community.faq.index.batch-size:20}")
	private int batchSize;

	@Value("${community.faq.index.batch-delay:1s}")
	private Duration batchDelay;

	@Value("${spring.ai.google.genai.embedding.text.model:gemini-embedding-2}")
	private String embeddingModel;

	@Async
	@TransactionalEventListener(fallbackExecution = true)
	public void onReindexRequested(FaqReindexRequestedEvent event) {
		int failed = 0;
		for (Long documentId : event.documentIds()) {
			try {
				DocumentSource source = stateService.startDocument(documentId);
				if (source == null) {
					log.info("FAQ 반영 중 삭제된 문서 건너뜀 documentId={}", documentId);
				} else {
					int chunkCount = index(source);
					stateService.completeDocument(documentId);
					log.info("FAQ 문서 반영 완료 documentId={}, 조각 {}개", documentId, chunkCount);
				}
			} catch (Exception e) {
				failed++;
				log.warn("FAQ 문서 반영 실패 documentId={}", documentId, e);
				stateService.failDocument(documentId, reasonOf(e));
				safeDeleteChunks(documentId);   // 일부만 저장된 조각 정리
			}
			stateService.progressJob(event.jobId());
		}
		stateService.finishJob(event.jobId(), failed);
		answerCache.bumpVersion();   // 문서가 바뀌었으니 예전 답변 캐시는 안 쓰기
	}

	private int index(DocumentSource source) throws InterruptedException {
		String text = textExtractor.extract(source.fileData(), source.fileType(), source.fileName());
		if (text == null || text.isBlank()) {
			throw new FaqIndexException("텍스트를 추출할 수 없는 파일입니다. (스캔한 이미지 PDF는 지원하지 않아요)");
		}
		List<String> pieces = textSplitter.split(source.title(), text);
		if (pieces.isEmpty()) {
			throw new FaqIndexException("반영할 내용이 없는 파일입니다.");
		}

		List<Document> documents = new ArrayList<>();
		for (int i = 0; i < pieces.size(); i++) {
			documents.add(new Document(pieces.get(i), metadata(source, i)));
		}

		vectorService.deleteChunks(source.id());   // 다시 반영이면 예전 조각 지우고 새로
		for (int from = 0; from < documents.size(); from += batchSize) {
			List<Document> batch = documents.subList(from, Math.min(from + batchSize, documents.size()));
			addWithRetry(batch);
			if (from + batchSize < documents.size()) {
				Thread.sleep(batchDelay.toMillis());
			}
		}
		return documents.size();
	}

	/** metadata 는 null 값을 넣을 수 없어서 공통 문서는 building_id 를 아예 빼고 저장 */
	private Map<String, Object> metadata(DocumentSource source, int chunkNo) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(FaqVectorService.DOCUMENT_ID, source.id());
		metadata.put("title", source.title());
		metadata.put("scope", source.scope().name());
		if (source.scope() == FaqScope.BUILDING && source.buildingId() != null) {
			metadata.put("building_id", source.buildingId());
		}
		metadata.put("chunk_no", chunkNo);
		metadata.put("embedding_model", embeddingModel);
		return metadata;
	}

	private void addWithRetry(List<Document> batch) throws InterruptedException {
		for (int attempt = 0; ; attempt++) {
			try {
				vectorStore.add(batch);
				return;
			} catch (RuntimeException e) {
				if (attempt >= RETRY_WAITS.size()) {
					throw e;
				}
				Duration wait = RETRY_WAITS.get(attempt);
				log.warn("임베딩 저장 실패 → {}초 후 다시 시도 ({}/{}): {}", wait.toSeconds(), attempt + 1,
						RETRY_WAITS.size(), e.getMessage());
				Thread.sleep(wait.toMillis());
			}
		}
	}

	private void safeDeleteChunks(Long documentId) {
		try {
			vectorService.deleteChunks(documentId);
		} catch (Exception e) {
			log.warn("실패한 문서 조각 정리 실패 documentId={}", documentId, e);
		}
	}

	/** 화면에 보여줄 실패 사유 */
	private String reasonOf(Exception e) {
		if (e instanceof FaqIndexException) {
			return e.getMessage();
		}
		String message = String.valueOf(e.getMessage());
		if (message.contains("429") || message.toLowerCase().contains("quota") || message.contains("RESOURCE_EXHAUSTED")) {
			return "AI 사용량 한도를 넘었어요. 잠시 후(또는 내일) 다시 반영해 주세요.";
		}
		if (message.contains("401") || message.contains("403") || message.toLowerCase().contains("api key")) {
			return "AI 키 설정을 확인해 주세요. (GEMINI_API_KEY)";
		}
		return "반영 중 오류가 발생했어요: " + (message.length() <= 200 ? message : message.substring(0, 200) + "…");
	}

	static class FaqIndexException extends RuntimeException {

		FaqIndexException(String message) {
			super(message);
		}
	}
}
