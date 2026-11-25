package com.coliving.platform.user.mock;

/**
 * 계약 서비스 입주 정보. 연동 전에는 프로필 응답에 이 값을 넣는다.
 *
 * @author 조휘재
 * 작성일: 2026-11-02
 */
public record ResidenceMock(String buildingName, String roomNumber) {

    /** 계약 서비스 연동 전 프로필에 넣는 고정 입주 정보. */
    public static ResidenceMock placeholder() {
        return new ResidenceMock("망원 스테이", "302호");
    }
}
