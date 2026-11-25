package com.coliving.platform.faq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 7-1 FAQ 봇 질문 — buildingId: 건물 상세 페이지·관리자 미리보기 (입주자는 생략 시 입주 건물) */
public record FaqAskRequestDTO(
		@NotBlank(message = "질문을 입력해 주세요.")
		@Size(max = 300, message = "질문은 300자 이하로 입력해 주세요.")
		String question,

		Long buildingId
) {
}
