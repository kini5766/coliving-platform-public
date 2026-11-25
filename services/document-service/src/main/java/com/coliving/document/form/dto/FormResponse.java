package com.coliving.document.form.dto;

import com.coliving.document.form.entity.DocumentForm;

import java.time.OffsetDateTime;

/** 양식 조회·등록·수정 응답. 엔티티를 그대로 내보내지 않고 이 틀로 옮긴다. */
public record FormResponse(
        Long id,                        // 양식 ID
        String language,                // 양식 언어
        String title,                   // 제목
        String content,                 // 본문
        OffsetDateTime createdAt,       // 생성 시각
        OffsetDateTime updatedAt        // 최종 수정 시각
) {
    /** 엔티티를 응답 틀로 옮긴다. */
    public static FormResponse from(DocumentForm form) {
        return new FormResponse(form.getId(), form.getLanguage(), form.getTitle(),
                form.getContent(), form.getCreatedAt(), form.getUpdatedAt());
    }
}