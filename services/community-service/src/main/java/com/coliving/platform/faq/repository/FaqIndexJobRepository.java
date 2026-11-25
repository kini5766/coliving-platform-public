package com.coliving.platform.faq.repository;

import com.coliving.platform.faq.entity.FaqIndexJobEntity;
import com.coliving.platform.faq.entity.FaqIndexStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqIndexJobRepository extends JpaRepository<FaqIndexJobEntity, Long> {

	boolean existsByStatus(FaqIndexStatus status);

	List<FaqIndexJobEntity> findByStatus(FaqIndexStatus status);
}
