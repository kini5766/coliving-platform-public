package com.coliving.dashboard.property;

import com.coliving.platform.grpc.property.ListBuildingRoomsRequest;
import com.coliving.platform.grpc.property.ListBuildingRoomsResponse;
import com.coliving.platform.grpc.property.PropertyServiceGrpc;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/** 매물 서비스에서 건물 하나의 호실 목록을 조회한다. */
@Service
public class PropertyRoomClient {

    private static final long DEADLINE_SECONDS = 2;

    private final PropertyServiceGrpc.PropertyServiceBlockingStub stub;

    public PropertyRoomClient(PropertyServiceGrpc.PropertyServiceBlockingStub stub) {
        this.stub = stub;
    }

    public BuildingRoomList listBuildingRooms(long buildingId) {
        ListBuildingRoomsRequest request = ListBuildingRoomsRequest.newBuilder()
                .setBuildingId(buildingId)
                .build();
        try {
            ListBuildingRoomsResponse response = stub
                    .withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .listBuildingRooms(request);
            long respondedBuildingId = response.getBuildingId() != 0 ? response.getBuildingId() : buildingId;
            var rooms = response.getRoomsList().stream()
                    .map(room -> new BuildingRoomList.Room(room.getRoomId(), room.getFloor(), room.getRoomName()))
                    .toList();
            return new BuildingRoomList(respondedBuildingId, response.getBuildingName(), rooms);
        } catch (StatusRuntimeException e) {
            throw new PropertyRoomsException(
                    "매물 서비스 호실 목록 조회 실패 (" + e.getStatus().getCode() + ")", e);
        }
    }
}
