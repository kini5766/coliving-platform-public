package com.coliving.platform.faq.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버가 반영 도중 꺼지면 작업이 계속 PROCESSING 으로 남아 다시 반영할 수 없음(409)
 * → 서버 시작 시 그런 작업·문서를 FAILED 로 바꿔서 다시 반영할 수 있게
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaqIndexRecovery {

	private final FaqIndexStateService stateService;

	@EventListener(ApplicationReadyEvent.class)
	public void abortInterruptedJobs() {
		int count = stateService.abortRunning("서버가 다시 시작돼 반영이 중단됐어요. 다시 반영해 주세요.");
		if (count > 0) {
			log.warn("중단된 FAQ 반영 작업 {}건을 실패 처리했어요", count);
		}
	}
}
