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

/** FAQ 반영 작업 (7-6 시작 → 7-7 진행 상태). created_at = 시작 일시 */
@Entity
@Table(name = "faq_index_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqIndexJobEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false, columnDefinition = "faq_index_status")
	private FaqIndexStatus status;

	@Column(nullable = false)
	private Long requestedBy;

	@Column(nullable = false)
	private int processedCount;

	@Column(nullable = false)
	private int totalCount;

	@Column(columnDefinition = "text")
	private String errorMessage;

	private LocalDateTime finishedAt;

	public static FaqIndexJobEntity start(Long requestedBy, int totalCount) {
		FaqIndexJobEntity job = new FaqIndexJobEntity();
		job.status = FaqIndexStatus.PROCESSING;
		job.requestedBy = requestedBy;
		job.totalCount = totalCount;
		return job;
	}

	public void progress() {
		this.processedCount++;
	}

	/** 실패한 문서가 있으면 FAILED + 사유 (성공한 문서는 그대로 반영됨) */
	public void finish(int failedCount) {
		this.status = failedCount == 0 ? FaqIndexStatus.COMPLETED : FaqIndexStatus.FAILED;
		this.errorMessage = failedCount == 0 ? null
				: totalCount + "개 중 " + failedCount + "개 문서를 반영하지 못했어요. 문서 목록에서 실패 사유를 확인해 주세요.";
		this.finishedAt = LocalDateTime.now();
	}

	public void abort(String reason) {
		this.status = FaqIndexStatus.FAILED;
		this.errorMessage = reason;
		this.finishedAt = LocalDateTime.now();
	}
}
