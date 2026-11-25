package com.coliving.platform.client.grpc;

import com.coliving.platform.grpc.contract.ContractResidentServiceGrpc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * contract-service gRPC stub 등록 (community.clients.contract=grpc 일 때만)
 * target = "contract" → spring.grpc.client.channel.contract.* 설정(주소 localhost:9089)을 쓰는 채널
 */
@Configuration
@ConditionalOnProperty(name = "community.clients.contract", havingValue = "grpc")
@ImportGrpcClients(target = "contract", types = ContractResidentServiceGrpc.ContractResidentServiceBlockingStub.class)
public class ContractGrpcClientConfig {
}
