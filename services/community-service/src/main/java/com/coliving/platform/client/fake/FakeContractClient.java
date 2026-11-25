package com.coliving.platform.client.fake;

import com.coliving.platform.client.ContractClient;
import com.coliving.platform.client.PropertyClient;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * TODO: contract-service gRPC 연동이 안정되면 이 클래스 삭제
 * community.clients.contract=fake 일 때만 사용 (기본값). grpc 면 GrpcContractClient 사용
 * 기본 가짜 계약 + fake-users.yaml 에서 buildingId 가 있는 회원
 */
@Component
@ConditionalOnProperty(name = "community.clients.contract", havingValue = "fake", matchIfMissing = true)
public class FakeContractClient implements ContractClient {

	private static final Map<Long, ResidentRoom> DEFAULT_ROOMS = List.of(
			new ResidentRoom(5001L, 9101L, 1L, "망원 스테이", "302호", "ACTIVE", LocalDate.of(2025, 11, 3), LocalDate.of(2026, 11, 2)),
			new ResidentRoom(5002L, 9102L, 1L, "망원 스테이", "201호", "ACTIVE", LocalDate.of(2025, 9, 1), LocalDate.of(2026, 8, 31)),
			new ResidentRoom(5003L, 9103L, 1L, "망원 스테이", "405호", "ACTIVE", LocalDate.of(2025, 10, 1), LocalDate.of(2026, 9, 30)),
			new ResidentRoom(6001L, 9201L, 1L, "망원 스테이", "302호", "PAYMENT_PENDING", LocalDate.of(2025, 11, 3), LocalDate.of(2026, 11, 2))
	).stream().collect(Collectors.toMap(ResidentRoom::userId, Function.identity()));

	private final Map<Long, ResidentRoom> rooms;

	public FakeContractClient(FakeUsersLoader loader, PropertyClient propertyClient) {
		Map<Long, ResidentRoom> merged = new LinkedHashMap<>(DEFAULT_ROOMS);
		loader.users().stream()
				.filter(user -> user.buildingId() != null)
				.forEach(user -> merged.put(user.id(), new ResidentRoom(user.id(), null, user.buildingId(),
						propertyClient.getBuildingName(user.buildingId()), user.roomNumber(), "ACTIVE", null, null)));
		this.rooms = Collections.unmodifiableMap(merged);
	}

	@Override
	public Map<Long, ResidentRoom> getResidentRooms(Collection<Long> userIds) {
		return userIds.stream()
				.distinct()
				.filter(rooms::containsKey)
				.collect(Collectors.toMap(Function.identity(), rooms::get));
	}
}
