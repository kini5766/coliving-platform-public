package com.coliving.document.form.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 양식 등록 요청. 언어는 만들 때만 정할 수 있다. */
public record FormCreateRequest(
        @Pattern(regexp = "ko|en") String language,     // 양식 언어 (ko, en만 허용)
        @NotBlank @Size(max = 200) String title,        // 제목
        @NotBlank String content                        // 본문
) {
}