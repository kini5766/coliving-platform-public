package com.coliving.platform.property.repository;

import com.coliving.platform.property.domain.Building;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BuildingRepository extends JpaRepository<Building, Long>, JpaSpecificationExecutor<Building> {
    Optional<Building> findByIdAndDeletedFalse(Long id);

    List<Building> findAllByDeletedFalseOrderByIdAsc();
}
