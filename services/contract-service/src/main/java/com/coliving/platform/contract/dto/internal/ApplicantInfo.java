package com.coliving.platform.contract.dto.internal;

/**
 * 신청서 제출 시 신청자 스냅샷 (ApplicationService.submit에서 만든다).
 *
 * 값은 프론트가 user-service 계약 자동완성으로 미리 채우고 사용자가 고친 값을 그대로 받는다.
 * DB 컬럼이 nullable이라 필수 값은 여기서 거른다 (name·phone이 비어 있으면 IllegalArgumentException → 400).
 *
 * @param email   필수. 빈 값·형식 검사는 ApplicationService가 먼저 한다
 * @param address 선택 (없으면 null)
 * @author 한성현
 */
public record ApplicantInfo(
        String name,
        String phone,
        String email,
        String address
) {
    public ApplicantInfo {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("신청자 이름이 없습니다");
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("신청자 휴대폰 번호가 없습니다");
    }
}
