package com.coliving.platform.faq.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 같은 질문·같은 건물 답변 10분 캐시 (Redis) — AI 호출 줄이기
 * key: faq:answer:{문서버전}:{건물 또는 common}:{질문 sha256}
 * 문서를 다시 반영하거나 삭제하면 문서버전(faq:index-version)이 올라가서 예전 캐시는 자동으로 안 쓰임
 * 값: 1줄째 answered(Y/N) · 2줄째 근거 문서 ID(쉼표) · 3줄째부터 답변
 */
@Component
@RequiredArgsConstructor
public class FaqAnswerCache {

	private static final String VERSION_KEY = "faq:index-version";

	private final StringRedisTemplate redisTemplate;

	@Value("${community.faq.answer-cache-ttl:10m}")
	private Duration ttl;

	public Optional<CachedAnswer> get(Long buildingId, String question) {
		String value = redisTemplate.opsForValue().get(key(buildingId, question));
		if (value == null) {
			return Optional.empty();
		}
		String[] parts = value.split("\n", 3);
		if (parts.length < 3) {
			return Optional.empty();
		}
		List<Long> sourceIds = parts[1].isEmpty() ? List.of()
				: Arrays.stream(parts[1].split(",")).map(Long::valueOf).toList();
		return Optional.of(new CachedAnswer("Y".equals(parts[0]), parts[2], sourceIds));
	}

	public void put(Long buildingId, String question, boolean answered, String answer, List<Long> sourceIds) {
		String ids = String.join(",", sourceIds.stream().map(String::valueOf).toList());
		redisTemplate.opsForValue().set(key(buildingId, question), (answered ? "Y" : "N") + "\n" + ids + "\n" + answer, ttl);
	}

	/** 문서 반영·삭제 후 호출 → 예전 캐시 무효화 */
	public void bumpVersion() {
		redisTemplate.opsForValue().increment(VERSION_KEY);
	}

	private String key(Long buildingId, String question) {
		String version = Optional.ofNullable(redisTemplate.opsForValue().get(VERSION_KEY)).orElse("0");
		return "faq:answer:" + version + ":" + (buildingId == null ? "common" : buildingId) + ":" + sha256(normalize(question));
	}

	/** 띄어쓰기·물음표 차이는 같은 질문으로 */
	static String normalize(String question) {
		return question.strip().replaceAll("\\s+", " ").replaceAll("[?？!.]+$", "").toLowerCase();
	}

	private static String sha256(String text) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public record CachedAnswer(boolean answered, String answer, List<Long> sourceIds) {
	}
}
