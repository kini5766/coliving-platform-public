package com.coliving.platform.property.internal;

import com.coliving.platform.grpc.property.BuildingSummary;
import com.coliving.platform.grpc.property.GetBuildingsResponse;
import com.coliving.platform.property.domain.Building;
import com.coliving.platform.property.repository.BuildingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 커뮤니티용 건물 목록. is_deleted 인 건물은 빼서 보낸다. */
@Service
@Transactional(readOnly = true)
public class GetBuildingsService {

    private final BuildingRepository buildings;

    public GetBuildingsService(BuildingRepository buildings) {
        this.buildings = buildings;
    }

    public GetBuildingsResponse getBuildings() {
        GetBuildingsResponse.Builder response = GetBuildingsResponse.newBuilder();
        for (Building building : buildings.findAllByDeletedFalseOrderByIdAsc()) {
            response.addBuildings(toSummary(building));
        }
        return response.build();
    }

    private static BuildingSummary toSummary(Building building) {
        String name = building.getName() == null ? "" : building.getName();
        return BuildingSummary.newBuilder()
                .setBuildingId(building.getId())
                .setBuildingName(name)
                .build();
    }
}
