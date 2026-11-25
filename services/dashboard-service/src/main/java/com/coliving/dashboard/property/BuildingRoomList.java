package com.coliving.dashboard.property;

import java.util.List;

/** 매물 서비스가 내려준 건물 하나의 호실 목록. 입주 상태는 포함하지 않는다. */
public record BuildingRoomList(long buildingId, String buildingName, List<Room> rooms) {

    public BuildingRoomList {
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
    }

    public record Room(long roomId, int floor, String roomName) {
    }
}
