package com.coliving.platform.client;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * user-service 호출 (연동 요청 API 1-1, 1-2)
 * 지금은 FakeUserClient가 가짜 데이터를 돌려줌 → gRPC 서버가 나오면 GrpcUserClient로 교체
 */
public interface UserClient {

	/** 회원 일괄 조회 — 없는 ID는 결과에서 빠짐 */
	Map<Long, UserSummary> getUsers(Collection<Long> userIds);

	/** 관리자 쪽지 쓰기 — 이름 검색 */
	List<UserSummary> searchUsers(String keyword);

	default UserSummary getUser(Long userId) {
		return getUsers(List.of(userId)).get(userId);
	}

	/**
	 * @param roles USER / CANDIDATE / RESIDENT / ADMIN 중 여러 개
	 *              (예: 계약 연장·이사 중인 입주자 = [USER, CANDIDATE, RESIDENT])
	 *              RESIDENT 는 실제 입주일부터 퇴거일까지만 붙음 → 커뮤니티 사용 가능 여부
	 */
	record UserSummary(Long userId, String name, String profileImageUrl, List<String> roles) {

		public static final String USER = "USER";
		public static final String CANDIDATE = "CANDIDATE";
		public static final String RESIDENT = "RESIDENT";
		public static final String ADMIN = "ADMIN";

		public UserSummary {
			roles = roles == null ? List.of() : List.copyOf(roles);
		}

		public boolean hasRole(String role) {
			return roles.contains(role);
		}

		/** 실제 거주 중 (커뮤니티 게시판·채팅 사용 가능) */
		public boolean isResident() {
			return hasRole(RESIDENT);
		}

		public boolean isAdmin() {
			return hasRole(ADMIN);
		}
	}
}
