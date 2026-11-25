package com.coliving.platform.faq.dto;

import java.util.List;

/**
 * 7-1 FAQ 봇 답변
 * answered: 문서 근거로 답했는지 / actions: 답을 못 했을 때 다음 행동 버튼 (INQUIRY 비회원 문의, NOTE 쪽지 문의)
 */
public record FaqAskResponseDTO(
		boolean answered,
		String answer,
		List<FaqSourceDTO> sources,
		List<String> actions,
		boolean cached
) {
}
