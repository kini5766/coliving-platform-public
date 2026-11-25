package com.coliving.platform.common.dto;

import java.util.List;

/**
 * 5-11 내 커뮤니티 권한 — 화면이 커뮤니티 메뉴·게시판·채팅을 보여줄지 정할 때 사용
 * resident: 게시판·채팅 사용 가능 / manager: 매니저(관리자)로 게시판 사용 / known: 커뮤니티 서버가 아는 회원인지
 */
public record CommunityMeDTO(
		Long userId,
		boolean known,
		String name,
		List<String> roles,
		boolean resident,
		boolean manager,
		Long buildingId,
		String buildingName,
		String roomNumber
) {
}
