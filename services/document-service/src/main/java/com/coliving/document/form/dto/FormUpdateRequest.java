package com.coliving.document.form.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 양식 수정 요청. 제목과 본문만 고칠 수 있다. */
public record FormUpdateRequest(
        @NotBlank @Size(max = 200) String title,        // 제목
        @NotBlank String content                        // 본문
) {
}