package com.coliving.platform.client.grpc;

import com.coliving.platform.client.UserClient;
import com.coliving.platform.grpc.user.GetUsersRequest;
import com.coliving.platform.grpc.user.SearchUsersRequest;
import com.coliving.platform.grpc.user.UserServiceGrpc;
import com.coliving.platform.common.exception.CommunityException;
import com.coliving.platform.common.exception.ErrorCode;
import io.grpc.StatusRuntimeException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * user-service gRPC 클라이언트 (연동 요청 API 1-1 GetUsers, 1-2 SearchUsers)
 * community.clients.user=grpc 일 때 FakeUserClient 대신 사용
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "community.clients.user", havingValue = "grpc")
public class GrpcUserClient implements UserClient {

	/** proto 규칙: 한 번에 최대 100명 */
	private static final int MAX_IDS_PER_CALL = 100;
	private static final int SEARCH_SIZE = 10;
	private static final long DEADLINE_SECONDS = 3;

	private final UserServiceGrpc.UserServiceBlockingStub userStub;

	public GrpcUserClient(UserServiceGrpc.UserServiceBlockingStub userStub) {
		this.userStub = userStub;
		log.info("회원 정보는 user-service gRPC 로 조회해요 (community.clients.user=grpc)");
	}

	@Override
	public Map<Long, UserSummary> getUsers(Collection<Long> userIds) {
		Map<Long, UserSummary> result = new LinkedHashMap<>();
		if (userIds == null || userIds.isEmpty()) {
			return result;
		}
		List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
		for (int from = 0; from < ids.size(); from += MAX_IDS_PER_CALL) {
			List<Long> chunk = ids.subList(from, Math.min(from + MAX_IDS_PER_CALL, ids.size()));
			try {
				userStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
						.getUsers(GetUsersRequest.newBuilder().addAllUserIds(chunk).build())
						.getUsersList()
						.forEach(user -> result.put(user.getUserId(), toSummary(user)));
			} catch (StatusRuntimeException e) {
				log.warn("user-service GetUsers 실패: {} (ids {}개)", e.getStatus(), chunk.size());
				throw new CommunityException(ErrorCode.SERVICE_UNAVAILABLE, "회원 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
			}
		}
		return result;   // 없는 ID 는 빠져 있음 → 호출한 쪽에서 "알 수 없는 사용자"
	}

	@Override
	public List<UserSummary> searchUsers(String keyword) {
		String trimmed = keyword == null ? "" : keyword.strip();
		if (trimmed.length() < 2) {   // 서버 규칙: 2자 이상
			return List.of();
		}
		try {
			return userStub.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS)
					.searchUsers(SearchUsersRequest.newBuilder().setKeyword(trimmed).setSize(SEARCH_SIZE).build())
					.getUsersList()
					.stream()
					.map(GrpcUserClient::toSummary)
					.filter(user -> !user.isAdmin())   // 관리자에게 쪽지 쓰기 대상 아님 (FakeUserClient 와 동일)
					.toList();
		} catch (StatusRuntimeException e) {
			log.warn("user-service SearchUsers 실패: {}", e.getStatus());
			throw new CommunityException(ErrorCode.SERVICE_UNAVAILABLE, "회원 검색에 실패했어요. 잠시 후 다시 시도해 주세요.");
		}
	}

	private static UserSummary toSummary(com.coliving.platform.grpc.user.UserSummary user) {
		String profileImageUrl = user.hasProfileImageUrl() && !user.getProfileImageUrl().isBlank()
				? user.getProfileImageUrl()
				: null;
		return new UserSummary(user.getUserId(), user.getName(), profileImageUrl, List.copyOf(user.getRolesList()));
	}
}
