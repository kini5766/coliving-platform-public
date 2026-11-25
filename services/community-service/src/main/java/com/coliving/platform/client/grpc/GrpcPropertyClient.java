package com.coliving.platform.client.grpc;

import com.coliving.platform.client.PropertyClient;
import com.coliving.platform.grpc.property.BuildingServiceGrpc;
import com.coliving.platform.grpc.property.GetBuildingsRequest;
import com.coliving.platform.common.exception.CommunityException;
import com.coliving.platform.common.exception.ErrorCode;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * property-service gRPC 클라이언트 (연동 요청 API 3-1 GetBuildings)
 * community.clients.property=grpc 일 때 FakePropertyClient 대신 사용
 *
 * 건물 목록은 거의 안 바뀌는데 getBuildingName() 이 목록 화면에서 여러 번 불려서 30초 동안 메모리에 보관
 * property-service 가 잠깐 꺼져 있으면 마지막으로 받은 목록을 그대로 씀 (한 번도 못 받았으면 오류)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "community.clients.property", havingValue = "grpc")
public class GrpcPropertyClient implements PropertyClient {

	private static final Duration CACHE_TTL = Duration.ofSeconds(30);
	private static final long DEADLINE_SECONDS = 3;

	private final BuildingServiceGrpc.BuildingServiceBlockingStub buildingStub;

	private volatile List<BuildingSummary> cached;
	private volatile Instant cachedAt = Instant.EPOCH;

	public GrpcPropertyClient(BuildingServiceGrpc.BuildingServiceBlockingStub buildingStub) {
		this.buildingStub = buildingStub;
		log.info("건물 목록은 property-service gRPC 로 조회해요 (community.clients.property=grpc)");
	}

	@Override
	public List<BuildingSummary> getBuildings() {
		List<BuildingSummary> current = cached;
		if (current != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
			return current;
		}
		try {
			List<BuildingSummary> fresh = buildingStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
					.getBuildings(GetBuildingsRequest.getDefaultInstance())
					.getBuildingsList()
					.stream()
					.map(building -> new BuildingSummary(building.getBuildingId(), building.getBuildingName()))
					.toList();
			cached = fresh;
			cachedAt = Instant.now();
			return fresh;
		} catch (StatusRuntimeException e) {
			if (current != null) {
				log.warn("property-service GetBuildings 실패: {} → 마지막 목록 사용", e.getStatus());
				return current;
			}
			log.warn("property-service GetBuildings 실패: {}", e.getStatus());
			throw new CommunityException(ErrorCode.SERVICE_UNAVAILABLE, "건물 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
		}
	}
}
