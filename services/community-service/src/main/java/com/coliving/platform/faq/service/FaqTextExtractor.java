package com.coliving.platform.faq.service;

import com.coliving.platform.faq.entity.FaqFileType;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/**
 * 원본 파일 → 텍스트
 * TXT·MD 는 UTF-8 그대로, PDF·DOCX 는 Apache Tika (Spring AI TikaDocumentReader)
 * 스캔 이미지 PDF 처럼 글자가 없으면 빈 문자열 → 반영 실패 처리
 */
@Component
public class FaqTextExtractor {

	public String extract(byte[] fileData, FaqFileType fileType, String fileName) {
		if (fileType.isPlainText()) {
			String text = new String(fileData, StandardCharsets.UTF_8);
			return text.startsWith("\uFEFF") ? text.substring(1) : text;   // BOM 제거
		}
		ByteArrayResource resource = new ByteArrayResource(fileData) {
			@Override
			public String getFilename() {
				return fileName;
			}
		};
		return new TikaDocumentReader(resource).get().stream()
				.map(Document::getText)
				.filter(text -> text != null && !text.isBlank())
				.collect(Collectors.joining("\n\n"));
	}
}
