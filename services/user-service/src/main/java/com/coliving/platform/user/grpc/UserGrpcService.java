package com.coliving.platform.user.grpc;

import com.coliving.platform.grpc.user.GetUsersRequest;
import com.coliving.platform.grpc.user.GetUsersResponse;
import com.coliving.platform.grpc.user.SearchUsersRequest;
import com.coliving.platform.grpc.user.SearchUsersResponse;
import com.coliving.platform.grpc.user.UserServiceGrpc;
import com.coliving.platform.user.internal.GetUsersService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

/**
 * 다른 서비스의 회원 조회·닉네임 검색. HTTP 가 아니라 gRPC 다.
 *
 * @author 조휘재
 * 작성일: 2026-11-03
 */
@GrpcService
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final GetUsersService getUsersService;

    /** 조회 구현을 gRPC 스텁에 연결한다. */
    public UserGrpcService(GetUsersService getUsersService) {
        this.getUsersService = getUsersService;
    }

    /** 회원 번호 목록으로 요약을 돌려준다. 잘못된 인자는 INVALID_ARGUMENT. */
    @Override
    public void getUsers(GetUsersRequest request, StreamObserver<GetUsersResponse> responseObserver) {
        try {
            responseObserver.onNext(getUsersService.getUsers(request));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription("회원 조회에 실패했습니다.").asRuntimeException());
        }
    }

    /** 닉네임으로 회원을 찾는다. 잘못된 인자는 INVALID_ARGUMENT. */
    @Override
    public void searchUsers(SearchUsersRequest request, StreamObserver<SearchUsersResponse> responseObserver) {
        try {
            responseObserver.onNext(getUsersService.searchUsers(request));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(ex.getMessage()).asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL.withDescription("회원 검색에 실패했습니다.").asRuntimeException());
        }
    }
}
