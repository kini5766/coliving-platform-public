package com.coliving.platform.faq.entity;

import com.coliving.platform.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** FAQ 봇 질문 기록 — 7-1 질문마다 저장(COL-132), 7-8 관리자 조회 */
@Entity
@Table(name = "faq_question_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FaqQuestionLogEntity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 300)
	private String question;

	private Long buildingId;

	/** 비회원은 null — 화면에는 표시하지 않음 (개인정보 최소화) */
	private Long userId;

	@Column(nullable = false)
	private boolean answered;

	private Float topScore;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "bigint[]")
	private Long[] sourceDocIds;

	@Column(nullable = false)
	private boolean cached;

	private Integer latencyMs;

	public static FaqQuestionLogEntity create(String question, Long buildingId, Long userId, boolean answered,
			Float topScore, List<Long> sourceDocIds, boolean cached, int latencyMs) {
		FaqQuestionLogEntity log = new FaqQuestionLogEntity();
		log.question = question;
		log.buildingId = buildingId;
		log.userId = userId;
		log.answered = answered;
		log.topScore = topScore;
		log.sourceDocIds = sourceDocIds.toArray(Long[]::new);
		log.cached = cached;
		log.latencyMs = latencyMs;
		return log;
	}

	public List<Long> getSourceDocIdList() {
		return sourceDocIds == null ? List.of() : List.of(sourceDocIds);
	}
}
