package com.coliving.platform.client.fake;

import com.coliving.platform.client.PropertyClient;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * TODO: property-service gRPC 연동이 안정되면 이 클래스 삭제
 * community.clients.property=fake 일 때만 사용 (기본값). grpc 면 GrpcPropertyClient 사용
 */
@Component
@ConditionalOnProperty(name = "community.clients.property", havingValue = "fake", matchIfMissing = true)
public class FakePropertyClient implements PropertyClient {

	@Override
	public List<BuildingSummary> getBuildings() {
		return List.of(
				new BuildingSummary(1L, "망원 스테이"),
				new BuildingSummary(2L, "성수 하우스"),
				new BuildingSummary(3L, "연남 리빙")
		);
	}
}
