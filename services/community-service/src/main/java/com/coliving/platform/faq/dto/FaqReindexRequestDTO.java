package com.coliving.platform.faq.dto;

import java.util.List;

/** 7-6 반영 시작 — documentIds 생략 시 대기·실패 문서 전체 */
public record FaqReindexRequestDTO(List<Long> documentIds) {
}
