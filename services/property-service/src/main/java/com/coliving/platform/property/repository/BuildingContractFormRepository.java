package com.coliving.platform.property.repository;

import com.coliving.platform.property.domain.BuildingContractForm;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuildingContractFormRepository extends JpaRepository<BuildingContractForm, Long> {
    List<BuildingContractForm> findByBuildingId(Long buildingId);

    void deleteByBuildingId(Long buildingId);
}
