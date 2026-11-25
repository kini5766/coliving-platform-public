package com.coliving.platform.faq.controller;

import com.coliving.platform.faq.dto.FaqAskRequestDTO;
import com.coliving.platform.faq.dto.FaqAskResponseDTO;
import com.coliving.platform.faq.service.FaqBotService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 7-1 FAQ 봇 질문 — 비회원 포함 누구나 (X-User 없으면 비회원) */
@RestController
@RequestMapping("/community-service/faq-bot")
@RequiredArgsConstructor
public class FaqBotController {

	private final FaqBotService faqBotService;

	@PostMapping("/ask")
	public FaqAskResponseDTO ask(@RequestHeader(value = "X-User", required = false) Long userId,
			@Valid @RequestBody FaqAskRequestDTO request, HttpServletRequest httpRequest) {
		return faqBotService.ask(userId, clientIp(httpRequest), request.question(), request.buildingId());
	}

	/** 비회원 횟수 제한용 IP — Gateway 를 거치면 X-Forwarded-For 첫 번째 값 */
	private String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].strip();
		}
		return request.getRemoteAddr();
	}
}
