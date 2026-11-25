package com.coliving.platform.faq.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 문서 텍스트 → 검색용 조각
 * - 줄 단위로 모아서 약 chunkSize 자씩 (한국어는 토큰 기준보다 글자 기준이 예측하기 쉬움)
 * - 제목(# 제목, "제3조", "1. 계약" 같은 짧은 줄)을 기억해서 조각 앞에 "[문서 제목 > 섹션]" 을 붙임
 *   → 조각만 봐도 어느 문서·어느 항목인지 알 수 있어 검색·답변 정확도가 올라감
 * - 너무 긴 줄은 문장 단위로 자름
 */
@Component
public class FaqTextSplitter {

	private static final Pattern HEADING = Pattern.compile(
			// "# 제목" · "제3조 (택배)" · "1. 계약 기간" · "Q. 보증금은 언제 돌려받나요?" 같은 짧은 줄
			"^(#{1,6}\\s+.+|제\\s*\\d+\\s*[조장절항].*|\\d{1,2}[.)]\\s+\\S.*|Q\\s*[.:]?\\s*.+\\?)$");
	private static final int MIN_CHUNK_LENGTH = 5;

	private final int chunkSize;

	public FaqTextSplitter(@Value("${community.faq.index.chunk-size:600}") int chunkSize) {
		this.chunkSize = chunkSize;
	}

	public List<String> split(String documentTitle, String text) {
		List<String> chunks = new ArrayList<>();
		String section = null;
		StringBuilder current = new StringBuilder();

		// 한 줄씩 — PDF·DOCX 는 문단이 빈 줄 없이 줄바꿈만으로 오는 경우가 많아서 줄 단위로 처리
		for (String rawLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
			String line = rawLine.strip();
			if (line.isEmpty()) {
				continue;
			}
			if (isHeading(line)) {
				flush(chunks, documentTitle, section, current);   // 새 섹션 시작 → 지금까지 모은 것 저장
				section = line.replaceFirst("^#{1,6}\\s+", "");
				continue;
			}
			for (String piece : splitLong(line)) {
				if (current.length() > 0 && current.length() + piece.length() + 1 > chunkSize) {
					flush(chunks, documentTitle, section, current);
				}
				if (current.length() > 0) {
					current.append('\n');
				}
				current.append(piece);
			}
		}
		flush(chunks, documentTitle, section, current);
		return chunks;
	}

	private boolean isHeading(String line) {
		return line.length() <= 60 && HEADING.matcher(line).matches();
	}

	private void flush(List<String> chunks, String documentTitle, String section, StringBuilder current) {
		String body = current.toString().strip();
		current.setLength(0);
		if (body.isEmpty() || (body.length() < MIN_CHUNK_LENGTH && !chunks.isEmpty())) {
			return;   // 너무 짧은 조각은 버림 (문서 첫 조각은 남김)
		}
		String prefix = section == null ? "[" + documentTitle + "] " : "[" + documentTitle + " > " + section + "] ";
		chunks.add(prefix + body);
	}

	/** 한 줄이 chunkSize 를 넘으면 문장 끝(. ? !)에서 자름 */
	private List<String> splitLong(String paragraph) {
		if (paragraph.length() <= chunkSize) {
			return List.of(paragraph);
		}
		List<String> pieces = new ArrayList<>();
		StringBuilder piece = new StringBuilder();
		for (String sentence : paragraph.split("(?<=[.?!])\\s+|\\n")) {
			if (piece.length() > 0 && piece.length() + sentence.length() + 1 > chunkSize) {
				pieces.add(piece.toString().strip());
				piece.setLength(0);
			}
			// 문장 하나가 너무 길면 글자 수로 자름
			while (sentence.length() > chunkSize) {
				pieces.add(sentence.substring(0, chunkSize));
				sentence = sentence.substring(chunkSize);
			}
			piece.append(sentence).append(' ');
		}
		if (!piece.toString().isBlank()) {
			pieces.add(piece.toString().strip());
		}
		return pieces;
	}
}
