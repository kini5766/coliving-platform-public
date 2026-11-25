package com.coliving.platform.client.grpc;

import com.coliving.platform.grpc.property.BuildingServiceGrpc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * property-service gRPC stub 등록 (community.clients.property=grpc 일 때만)
 * target = "property" → spring.grpc.client.channel.property.* 설정을 쓰는 채널
 */
@Configuration
@ConditionalOnProperty(name = "community.clients.property", havingValue = "grpc")
@ImportGrpcClients(target = "property", types = BuildingServiceGrpc.BuildingServiceBlockingStub.class)
public class PropertyGrpcClientConfig {
}
