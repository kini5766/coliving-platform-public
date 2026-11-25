package com.coliving.platform.faq.repository;

import com.coliving.platform.faq.entity.FaqQuestionLogEntity;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqQuestionLogRepository extends JpaRepository<FaqQuestionLogEntity, Long> {

	@Query("select l from FaqQuestionLogEntity l "
			+ "where l.createdAt >= :from and l.createdAt < :to "
			+ "and (:buildingId is null or l.buildingId = :buildingId) "
			+ "and (:answered is null or l.answered = :answered) "
			+ "order by l.createdAt desc, l.id desc")
	Page<FaqQuestionLogEntity> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
			@Param("buildingId") Long buildingId, @Param("answered") Boolean answered, Pageable pageable);

	@Query("select new com.coliving.platform.faq.repository.FaqQuestionLogSummaryRow(count(l), "
			+ "sum(case when l.answered = false then 1 else 0 end), "
			+ "sum(case when l.cached = true then 1 else 0 end), avg(l.latencyMs)) "
			+ "from FaqQuestionLogEntity l "
			+ "where l.createdAt >= :from and l.createdAt < :to "
			+ "and (:buildingId is null or l.buildingId = :buildingId)")
	FaqQuestionLogSummaryRow summarize(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
			@Param("buildingId") Long buildingId);
}
