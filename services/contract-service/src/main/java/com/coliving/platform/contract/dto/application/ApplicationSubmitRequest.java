package com.coliving.platform.contract.dto.application;

import java.util.List;

/**
 * [1-2 신청서 제출] POST /contract-service/apply/applications/{applicationNo}/submit
 *
 * DRAFT → SUBMITTED. 이 시점에 결제 금액이 고정되고 계약서(contract_document)가 만들어진다.
 * 신청자 정보는 프론트가 user-service 계약 자동완성(JSON)으로 미리 채우고, 사용자가 고친 값을 그대로 보낸다.
 * 서버는 이 값을 신청서 스냅샷으로 저장한다. name·phone·email 필수, address 선택.
 *
 * @param contractLang 계약서 언어 ("ko" / "en"). 필수
 * @param termsVersion 동의한 약관 버전. ApplyFormResponse.termsVersion을 그대로 보낸다 (현재 버전과 다르면 400)
 * @author 한성현
 */
public record ApplicationSubmitRequest(
        String name,
        String phone,
        String email,
        String address,
        String contractLang,
        String  termsVersion
) {
}
