package com.coliving.platform.faq.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/** faq_chunks(pgvector) 에서 문서 조각 지우기 — faq_documents 와 FK 대신 metadata.document_id 로 연결 */
@Service
@RequiredArgsConstructor
public class FaqVectorService {

	public static final String DOCUMENT_ID = "document_id";

	private final VectorStore vectorStore;

	public void deleteChunks(Long documentId) {
		vectorStore.delete(new FilterExpressionBuilder().eq(DOCUMENT_ID, documentId).build());
	}

	public void deleteChunks(List<Long> documentIds) {
		documentIds.forEach(this::deleteChunks);
	}
}
