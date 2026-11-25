package com.coliving.platform.property.grpc;

import com.coliving.platform.grpc.property.BuildingServiceGrpc;
import com.coliving.platform.grpc.property.GetBuildingsRequest;
import com.coliving.platform.grpc.property.GetBuildingsResponse;
import com.coliving.platform.property.internal.GetBuildingsService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class BuildingGrpcService extends BuildingServiceGrpc.BuildingServiceImplBase {

    private final GetBuildingsService getBuildingsService;

    public BuildingGrpcService(GetBuildingsService getBuildingsService) {
        this.getBuildingsService = getBuildingsService;
    }

    @Override
    public void getBuildings(GetBuildingsRequest request, StreamObserver<GetBuildingsResponse> responseObserver) {
        try {
            responseObserver.onNext(getBuildingsService.getBuildings());
            responseObserver.onCompleted();
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription("건물 목록 조회에 실패했습니다.").asRuntimeException());
        }
    }
}
