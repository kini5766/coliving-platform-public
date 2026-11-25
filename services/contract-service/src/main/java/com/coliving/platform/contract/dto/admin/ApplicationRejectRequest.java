package com.coliving.platform.contract.dto.admin;

/**
 * [반려 - 관리자] POST /contract-service/admin/applications/{applicationId}/reject 요청
 * body: { "reason": "희망 입주일에 해당 호실을 이용할 수 없어요." }
 *
 * 처리한 관리자 ID는 body가 아니라 Gateway의 X-User 헤더로 받는다.
 * reason은 사용자 접수 화면(반려 사유)에 그대로 보인다.
 *
 * @author 한성현
 */
public record ApplicationRejectRequest(
        String reason
) {}
