package com.coliving.platform.faq.service;

import java.util.List;

/** 반영 작업 생성 완료 → 커밋 후 비동기 반영 시작 (FaqIndexWorker) */
public record FaqReindexRequestedEvent(Long jobId, List<Long> documentIds) {
}
