package com.coliving.platform.user.dto;

import com.coliving.platform.user.entity.ContractAutocompleteEntity;

/**
 * 계약서 자동완성 API 본문. 없는 값은 빈 문자열이다.
 *
 * @author 조휘재
 * 작성일: 2026-11-01
 */
public record ContractAutocompletePayload(
        String email,
        String name,
        String phone,
        String address,
        String contractLanguage
) {
    /** 아직 저장된 값이 없을 때의 본문. */
    public static ContractAutocompletePayload empty() {
        return new ContractAutocompletePayload("", "", "", "", "");
    }

    /** 저장된 행을 본문으로 바꾼다. null 칸은 빈 문자열이다. */
    public static ContractAutocompletePayload from(ContractAutocompleteEntity entity) {
        return new ContractAutocompletePayload(
                text(entity.getEmail()),
                text(entity.getName()),
                text(entity.getPhone()),
                text(entity.getAddress()),
                text(entity.getContractLanguage()));
    }

    /** null 을 빈 문자열로 바꾼다. */
    private static String text(String value) {
        return value == null ? "" : value;
    }
}
