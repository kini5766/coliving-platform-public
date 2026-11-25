package com.coliving.platform.contract.grpc;

import com.coliving.platform.contract.service.RoomOccupancyService;
import com.coliving.platform.grpc.contract.ContractOccupancyServiceGrpc;
import com.coliving.platform.grpc.contract.GetRoomAvailabilityRequest;
import com.coliving.platform.grpc.contract.GetRoomAvailabilityResponse;
import com.coliving.platform.grpc.contract.RoomAvailability;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * gRPC 서버: property-service(OccupancyClient)가 부르는 ContractOccupancyService (contract_occupancy_service.proto).
 * 포트는 application.yaml의 spring.grpc.server.port (9089). BindableService 빈이라 자동 등록된다.
 *
 * 에러 → gRPC 상태
 *   호실 ID 1000개 초과 → INVALID_ARGUMENT
 *   그 외 예외          → INTERNAL (매물 서비스는 입주 가능 표시를 숨긴다)
 *
 * @author 한성현
 */
@Service
public class ContractOccupancyGrpcService extends ContractOccupancyServiceGrpc.ContractOccupancyServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ContractOccupancyGrpcService.class);

    private final RoomOccupancyService roomOccupancyService;

    public ContractOccupancyGrpcService(RoomOccupancyService roomOccupancyService) {
        this.roomOccupancyService = roomOccupancyService;
    }

    @Override
    public void getRoomAvailability(GetRoomAvailabilityRequest request,
                                    StreamObserver<GetRoomAvailabilityResponse> responseObserver) {
        GetRoomAvailabilityResponse.Builder response = GetRoomAvailabilityResponse.newBuilder();
        try {
            Map<Long, Boolean> availability = roomOccupancyService.getAvailability(request.getRoomIdsList());
            availability.forEach((roomId, available) -> response.addRooms(
                    RoomAvailability.newBuilder().setRoomId(roomId).setAvailable(available).build()));
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            return;
        } catch (RuntimeException e) {
            log.error("호실 입주 가능 여부 조회 실패: roomIds={}", request.getRoomIdsList(), e);
            responseObserver.onError(Status.INTERNAL.withDescription("입주 가능 여부 조회에 실패했습니다.").asRuntimeException());
            return;
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }
}
