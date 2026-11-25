package com.coliving.platform.faq.entity;

import java.util.Locale;

/** 파일 형식 (DB enum faq_file_type) */
public enum FaqFileType {
	PDF("application/pdf"),
	DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
	TXT("text/plain;charset=UTF-8"),
	MD("text/markdown;charset=UTF-8");

	private final String contentType;

	FaqFileType(String contentType) {
		this.contentType = contentType;
	}

	public String contentType() {
		return contentType;
	}

	/** 파일 확장자로 형식 판단 — 지원하지 않으면 null */
	public static FaqFileType fromFileName(String fileName) {
		if (fileName == null || !fileName.contains(".")) {
			return null;
		}
		String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		return switch (extension) {
			case "pdf" -> PDF;
			case "docx" -> DOCX;
			case "txt" -> TXT;
			case "md", "markdown" -> MD;
			default -> null;
		};
	}

	public boolean isPlainText() {
		return this == TXT || this == MD;
	}
}
