package com.coliving.platform.client.fake;

import com.coliving.platform.client.UserClient;
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
 * TODO: user-service gRPC 연동이 안정되면 이 클래스 삭제
 * community.clients.user=fake 일 때만 사용 (기본값). grpc 면 GrpcUserClient 사용
 * 기본 가짜 회원(5001 등) + fake-users.yaml 의 실제 로그인 회원 (같은 ID면 yaml 우선)
 */
@Component
@ConditionalOnProperty(name = "community.clients.user", havingValue = "fake", matchIfMissing = true)
public class FakeUserClient implements UserClient {

	private static final Map<Long, UserSummary> DEFAULT_USERS = List.of(
			new UserSummary(5001L, "김민서", null, List.of("USER", "RESIDENT")),
			new UserSummary(5002L, "박준영", null, List.of("USER", "RESIDENT")),
			new UserSummary(5003L, "이도윤", null, List.of("USER", "CANDIDATE", "RESIDENT")),   // 계약 연장 진행 중
			new UserSummary(6001L, "김예린", null, List.of("USER", "CANDIDATE")),
			new UserSummary(7001L, "정하늘", null, List.of("USER")),
			new UserSummary(9001L, "망원 스테이 매니저", null, List.of("ADMIN"))
	).stream().collect(Collectors.toMap(UserSummary::userId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

	private final Map<Long, UserSummary> users;

	public FakeUserClient(FakeUsersLoader loader) {
		Map<Long, UserSummary> merged = new LinkedHashMap<>(DEFAULT_USERS);
		loader.users().forEach(user -> merged.put(user.id(),
				new UserSummary(user.id(), user.name(), null, user.roles())));
		this.users = Collections.unmodifiableMap(merged);   // 순서 유지
	}

	@Override
	public Map<Long, UserSummary> getUsers(Collection<Long> userIds) {
		return userIds.stream()
				.distinct()
				.filter(users::containsKey)
				.collect(Collectors.toMap(Function.identity(), users::get));
	}

	@Override
	public List<UserSummary> searchUsers(String keyword) {
		return users.values().stream()
				.filter(user -> !user.isAdmin())
				.filter(user -> keyword == null || user.name().contains(keyword))
				.toList();
	}
}
