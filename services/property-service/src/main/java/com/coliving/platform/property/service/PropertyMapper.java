package com.coliving.platform.property.service;

import com.coliving.platform.property.domain.Building;
import com.coliving.platform.property.domain.Room;
import com.coliving.platform.property.dto.PropertyDtos.FacilityDto;
import com.coliving.platform.property.dto.PropertyDtos.RoomSummary;
import java.util.List;
import java.util.Map;

final class PropertyMapper {

    private PropertyMapper() {}

    static List<FacilityDto> facilities(Building b) {
        return b.getFacilities().stream()
                .map(f -> new FacilityDto(f.getFacilityType(), f.getFacilityName()))
                .toList();
    }

    static List<FacilityDto> roomFacilities(Room r) {
        return r.getFacilities().stream()
                .map(f -> new FacilityDto(f.getFacilityType(), f.getFacilityName()))
                .toList();
    }

    static RoomSummary roomSummary(Room r, Map<Long, Boolean> availability) {
        return new RoomSummary(r.getId(), r.getRoomName(), r.getRoomType(), r.getLeaseType(), r.getMonthlyFee(),
                r.getDeposit(), r.getManagementFee(), r.getFloor(), r.getAreaM2(), r.getCapacity(),
                availability.get(r.getId()));
    }
}
