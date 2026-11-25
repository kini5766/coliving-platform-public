package com.coliving.platform.property.internal;

import com.coliving.platform.grpc.property.ListBuildingRoomsResponse;
import com.coliving.platform.property.domain.Building;
import com.coliving.platform.property.domain.Room;
import com.coliving.platform.property.repository.BuildingRepository;
import com.coliving.platform.property.repository.RoomRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대시보드용 건물 호실 목록. 삭제된 건물·호실은 빼서 보낸다. */
@Service
@Transactional(readOnly = true)
public class ListBuildingRoomsService {

    private final BuildingRepository buildings;
    private final RoomRepository rooms;

    public ListBuildingRoomsService(BuildingRepository buildings, RoomRepository rooms) {
        this.buildings = buildings;
        this.rooms = rooms;
    }

    public Optional<ListBuildingRoomsResponse> listBuildingRooms(long buildingId) {
        return buildings.findByIdAndDeletedFalse(buildingId).map(this::toResponse);
    }

    private ListBuildingRoomsResponse toResponse(Building building) {
        ListBuildingRoomsResponse.Builder response = ListBuildingRoomsResponse.newBuilder()
                .setBuildingId(building.getId())
                .setBuildingName(building.getName() == null ? "" : building.getName());
        for (Room room : rooms.findByBuildingIdAndDeletedFalseOrderByRoomName(building.getId())) {
            response.addRooms(toRoom(room));
        }
        return response.build();
    }

    private static com.coliving.platform.grpc.property.Room toRoom(Room room) {
        int floor = room.getFloor() == null ? 0 : room.getFloor();
        String name = room.getRoomName() == null ? "" : room.getRoomName();
        long roomId = room.getId() == null ? 0L : room.getId();
        return com.coliving.platform.grpc.property.Room.newBuilder()
                .setRoomId(roomId)
                .setFloor(floor)
                .setRoomName(name)
                .build();
    }
}
