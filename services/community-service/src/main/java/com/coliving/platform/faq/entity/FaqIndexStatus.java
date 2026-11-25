package com.coliving.platform.faq.entity;

/** 반영 상태 (DB enum faq_index_status) — 문서·반영 작업 공용 */
public enum FaqIndexStatus {
	PENDING,
	PROCESSING,
	COMPLETED,
	FAILED
}
