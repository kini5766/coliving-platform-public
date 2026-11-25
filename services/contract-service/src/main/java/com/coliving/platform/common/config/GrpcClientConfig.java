package com.coliving.platform.common.config;

import com.coliving.platform.grpc.document.DocumentServiceGrpc;

import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.ImportGrpcClients;

/**
 * gRPC 클라이언트(stub) 등록.
 *
 * Spring Boot 4.1부터는 stub을 자동으로 찾아 등록하지 않으므로 @ImportGrpcClients로 직접 등록한다.
 * target = "document" → application.yaml의 spring.grpc.client.channel.document.* 설정을 쓰는 채널에 연결된다.
 *
 * 등록되는 빈: DocumentServiceGrpc 의 BlockingStub
 * (호실 RoomServiceGrpc는 2026-11-05 제거: 매물 서비스에 서버가 없어 화면이 호실 정보를 보내는 방식으로 바꿈)
 *   (Blocking = 응답이 올 때까지 기다리는 동기 호출. Spring MVC 서비스라 이게 맞다)
 *
 * @author 한성현
 */
@Configuration
@ImportGrpcClients(target = "document", types = DocumentServiceGrpc.DocumentServiceBlockingStub.class)
public class GrpcClientConfig {
}


