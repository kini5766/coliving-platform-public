package com.coliving.platform.client.grpc;

import com.coliving.platform.grpc.user.UserServiceGrpc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * user-service gRPC stub 등록 (community.clients.user=grpc 일 때만)
 * target = "user" → spring.grpc.client.channel.user.* 설정(주소 localhost:9082)을 쓰는 채널
 * Spring Boot 4.1 은 stub 을 자동 등록하지 않아서 @ImportGrpcClients 로 직접 등록 (contract-service 와 같은 방식)
 */
@Configuration
@ConditionalOnProperty(name = "community.clients.user", havingValue = "grpc")
@ImportGrpcClients(target = "user", types = UserServiceGrpc.UserServiceBlockingStub.class)
public class UserGrpcClientConfig {
}
