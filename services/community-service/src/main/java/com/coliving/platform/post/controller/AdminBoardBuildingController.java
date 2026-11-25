package com.coliving.platform.post.controller;

import com.coliving.platform.client.PropertyClient;
import com.coliving.platform.common.auth.ResidentGuard;
import com.coliving.platform.post.dto.BoardBuildingDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매니저가 사용자 게시판(user-web)에서 볼 건물 선택 목록
 * TODO(연동 1-3): 관리자 담당 건물만 (지금은 전체 건물)
 */
@RestController
@RequestMapping("/community-service/admin/buildings")
@RequiredArgsConstructor
public class AdminBoardBuildingController {

	private final ResidentGuard residentGuard;
	private final PropertyClient propertyClient;

	@GetMapping
	public List<BoardBuildingDTO> getBuildings(@RequestHeader("X-User") Long userId) {
		residentGuard.requireAdmin(userId);
		return propertyClient.getBuildings().stream()
				.map(building -> new BoardBuildingDTO(building.buildingId(), building.name()))
				.toList();
	}
}
