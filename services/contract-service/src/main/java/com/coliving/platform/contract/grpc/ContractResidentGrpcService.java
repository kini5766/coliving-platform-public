package com.coliving.platform.contract.grpc;

import com.coliving.platform.contract.entity.Contract;
import com.coliving.platform.contract.service.ResidentRoomService;
import com.coliving.platform.grpc.contract.ContractResidentServiceGrpc;
import com.coliving.platform.grpc.contract.GetResidentRoomsRequest;
import com.coliving.platform.grpc.contract.GetResidentRoomsResponse;
import com.coliving.platform.grpc.contract.ResidentRoom;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * gRPC 서버: community-service가 부르는 ContractResidentService (contract_resident_service.proto).
 * 포트는 application.yaml의 spring.grpc.server.port (9089).
 * BindableService 빈이면 Spring gRPC가 서버에 자동 등록한다.
 *
 * 에러 → gRPC 상태
 *   회원 ID 100개 초과 → INVALID_ARGUMENT
 *   그 외 예외         → INTERNAL (community는 "잠시 후 다시" 처리)
 *
 * @author 한성현
 */
@Service
public class ContractResidentGrpcService extends ContractResidentServiceGrpc.ContractResidentServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ContractResidentGrpcService.class);

    private final ResidentRoomService residentRoomService;

    public ContractResidentGrpcService(ResidentRoomService residentRoomService) {
        this.residentRoomService = residentRoomService;
    }

    @Override
    public void getResidentRooms(GetResidentRoomsRequest request,
                                 StreamObserver<GetResidentRoomsResponse> responseObserver) {
        GetResidentRoomsResponse.Builder response = GetResidentRoomsResponse.newBuilder();
        try {
            for (Contract c : residentRoomService.findResidentRooms(request.getUserIdsList())) {
                response.addRooms(toProto(c));
            }
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            return;
        } catch (RuntimeException e) {
            log.error("입주 호실 조회 실패: userIds={}", request.getUserIdsList(), e);
            responseObserver.onError(Status.INTERNAL.withDescription("입주 호실 조회에 실패했습니다.").asRuntimeException());
            return;
        }
        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    // proto3 문자열은 null을 넣으면 예외 → 값이 있을 때만 넣는다
    private static ResidentRoom toProto(Contract c) {
        ResidentRoom.Builder b = ResidentRoom.newBuilder()
                .setUserId(c.getUserId())
                .setContractId(c.getId())
                .setBuildingId(c.getBuildingId())
                .setBuildingName(nz(c.getBuildingName()))
                .setRoomId(c.getRoomId())
                .setRoomName(nz(c.getRoomName()))
                .setContractStatus(c.getStatus().name());
        if (c.getMoveInDate() != null) b.setMoveInDate(c.getMoveInDate().toString());     // yyyy-MM-dd
        if (c.getMoveOutDate() != null) b.setMoveOutDate(c.getMoveOutDate().toString());
        return b.build();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
