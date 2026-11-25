package com.coliving.platform.faq.entity;

import com.coliving.platform.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * FAQ 봇 참고 문서 — 원본 파일은 DB(BYTEA)에 저장
 * 목록(7-2)은 파일 바이트를 읽지 않도록 FaqDocumentRow 로 필요한 칸만 조회
 */
@Entity
@Table(name = "faq_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqDocumentEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String title;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "faq_scope")
	private FaqScope scope;

	private Long buildingId;

	@Column(nullable = false)
	private String fileName;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "faq_file_type")
	private FaqFileType fileType;

	@Column(nullable = false, columnDefinition = "bytea")
	private byte[] fileData;

	@Column(nullable = false)
	private long fileSize;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "faq_index_status")
	private FaqIndexStatus indexStatus;

	@Column(length = 500)
	private String indexError;

	@Column(nullable = false)
	private Long uploadedBy;

	private LocalDateTime indexedAt;

	public static FaqDocumentEntity create(String title, FaqScope scope, Long buildingId, String fileName,
			FaqFileType fileType, byte[] fileData, Long uploadedBy) {
		FaqDocumentEntity document = new FaqDocumentEntity();
		document.title = title;
		document.scope = scope;
		document.buildingId = scope == FaqScope.BUILDING ? buildingId : null;
		document.fileName = fileName;
		document.fileType = fileType;
		document.fileData = fileData;
		document.fileSize = fileData.length;
		document.indexStatus = FaqIndexStatus.PENDING;
		document.uploadedBy = uploadedBy;
		return document;
	}

	public void markProcessing() {
		this.indexStatus = FaqIndexStatus.PROCESSING;
		this.indexError = null;
	}

	public void markCompleted() {
		this.indexStatus = FaqIndexStatus.COMPLETED;
		this.indexError = null;
		this.indexedAt = LocalDateTime.now();
	}

	public void markFailed(String error) {
		this.indexStatus = FaqIndexStatus.FAILED;
		this.indexError = error == null ? "알 수 없는 오류" : error.length() <= 500 ? error : error.substring(0, 499) + "…";
	}
}
