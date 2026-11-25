package com.coliving.platform.faq.service;

import com.coliving.platform.common.exception.CommunityException;
import com.coliving.platform.common.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * FAQ 봇 호출 제한 (Redis)
 * - 분당 횟수: 비회원 5회(IP 기준), 회원 10회(회원 ID 기준) → 넘으면 429 — 관리자 미리보기는 제외
 * - 일일 AI 호출 상한: 하루 전체 N회 (무료 키 보호) → 넘으면 AI 를 부르지 않고 안내 문구
 */
@Component
@RequiredArgsConstructor
public class FaqRateLimiter {

	private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

	private final StringRedisTemplate redisTemplate;

	@Value("${community.faq.rate-limit.guest-per-minute:5}")
	private int guestPerMinute;

	@Value("${community.faq.rate-limit.member-per-minute:10}")
	private int memberPerMinute;

	@Value("${community.faq.daily-call-limit:1000}")
	private int dailyCallLimit;

	/** 분당 횟수 확인 — key 예: faq:rate:user:5001:202611021630 / faq:rate:guest:0:0:0:0:0:0:0:1:202611021630 */
	public void checkPerMinute(Long userId, String clientIp) {
		boolean member = userId != null;
		String key = "faq:rate:" + (member ? "user:" + userId : "guest:" + clientIp) + ":"
				+ LocalDateTime.now().format(MINUTE);
		Long count = redisTemplate.opsForValue().increment(key);
		if (count != null && count == 1) {
			redisTemplate.expire(key, Duration.ofMinutes(2));
		}
		int limit = member ? memberPerMinute : guestPerMinute;
		if (count != null && count > limit) {
			throw new CommunityException(ErrorCode.TOO_MANY_REQUESTS,
					"질문이 너무 많아요. 1분 뒤에 다시 물어봐 주세요. (분당 " + limit + "회)");
		}
	}

	/** 오늘 AI 호출 1회 사용 — 상한을 넘었으면 false */
	public boolean tryUseDailyCall() {
		String key = "faq:daily-calls:" + LocalDate.now().format(DAY);
		Long count = redisTemplate.opsForValue().increment(key);
		if (count != null && count == 1) {
			redisTemplate.expire(key, Duration.ofDays(2));
		}
		return count == null || count <= dailyCallLimit;
	}
}
