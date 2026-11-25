package com.coliving.platform.note.entity;

import java.util.List;

/** 쪽지함 유형 (DB enum note_thread_type) — MEMBER = 일반 회원(role USER) */
public enum NoteThreadType {
	CANDIDATE,
	RESIDENT,
	MEMBER,
	GUEST;

	/** 유저 서비스 roles → 쪽지함 유형 (여러 개면 입주자 > 후보자 > 회원 순) */
	public static NoteThreadType fromRoles(List<String> roles) {
		if (roles.contains("RESIDENT")) {
			return RESIDENT;
		}
		if (roles.contains("CANDIDATE")) {
			return CANDIDATE;
		}
		return MEMBER;
	}
}
