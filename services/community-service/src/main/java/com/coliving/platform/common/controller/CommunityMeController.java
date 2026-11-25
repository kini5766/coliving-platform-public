package com.coliving.platform.common.controller;

import com.coliving.platform.client.ContractClient;
import com.coliving.platform.client.ContractClient.ResidentRoom;
import com.coliving.platform.client.UserClient;
import com.coliving.platform.client.UserClient.UserSummary;
import com.coliving.platform.common.dto.CommunityMeDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 5-11 내 커뮤니티 권한
 * 로그인 정보(user-service session)에는 역할이 없어서, 커뮤니티를 쓸 수 있는지는 커뮤니티 서버가 알려줌
 * → 화면의 판단과 서버의 실제 권한 확인(ResidentGuard)이 같은 기준
 */
@RestController
@RequiredArgsConstructor
public class CommunityMeController {

	private final UserClient userClient;
	private final ContractClient contractClient;

	@GetMapping("/community-service/me")
	public CommunityMeDTO getMe(@RequestHeader("X-User") Long userId) {
		UserSummary user = userClient.getUser(userId);
		if (user == null) {
			// 커뮤니티 서버가 모르는 회원 (gRPC 연동 전에는 fake-users.yaml 에 없는 회원)
			return new CommunityMeDTO(userId, false, null, List.of(), false, false, null, null, null);
		}
		ResidentRoom room = user.isResident()
				? contractClient.getResidentRooms(List.of(userId)).get(userId)
				: null;
		boolean resident = user.isResident() && room != null;
		return new CommunityMeDTO(userId, true, user.name(), user.roles(), resident, user.isAdmin(),
				room != null ? room.buildingId() : null,
				room != null ? room.buildingName() : null,
				room != null ? room.roomNumber() : null);
	}
}
