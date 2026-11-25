package com.coliving.platform.faq.dto;

import com.coliving.platform.faq.entity.FaqIndexStatus;
import java.time.LocalDateTime;

/** 7-3 업로드 응답 */
public record FaqDocumentUploadResponseDTO(Long documentId, FaqIndexStatus indexStatus, LocalDateTime uploadedAt) {
}
