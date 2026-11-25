package com.coliving.platform.faq.controller;

import com.coliving.platform.common.auth.ResidentGuard;
import com.coliving.platform.faq.dto.FaqQuestionLogListResponseDTO;
import com.coliving.platform.faq.service.AdminFaqLogService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 7-8 FAQ 봇 질문 기록 — 관리자 */
@RestController
@RequestMapping("/community-service/admin/faq-bot/logs")
@RequiredArgsConstructor
public class AdminFaqLogController {

	private final AdminFaqLogService adminFaqLogService;
	private final ResidentGuard residentGuard;

	@GetMapping
	public FaqQuestionLogListResponseDTO getLogs(@RequestHeader("X-User") Long userId,
			@RequestParam(required = false) Boolean answered, @RequestParam(required = false) Long buildingId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		residentGuard.requireAdmin(userId);
		return adminFaqLogService.getLogs(answered, buildingId, from, to, page, size);
	}
}
