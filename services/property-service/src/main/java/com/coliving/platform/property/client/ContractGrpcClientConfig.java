package com.coliving.platform.property.client;

import com.coliving.platform.grpc.contract.ContractOccupancyServiceGrpc;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * 계약 서비스 gRPC stub 등록 (Spring Boot 4.1부터 stub 자동 등록이 없어 직접 등록).
 * target = "contract" → application.yaml의 spring.grpc.client.channel.contract.* 설정(주소 localhost:9089)을 쓰는 채널
 * 등록되는 빈: ContractOccupancyServiceGrpc의 BlockingStub (OccupancyClient가 사용)
 */
@Configuration
@ImportGrpcClients(target = "contract", types = ContractOccupancyServiceGrpc.ContractOccupancyServiceBlockingStub.class)
public class ContractGrpcClientConfig {
}
