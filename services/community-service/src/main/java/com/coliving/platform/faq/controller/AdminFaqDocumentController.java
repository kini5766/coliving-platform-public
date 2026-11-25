package com.coliving.platform.faq.controller;

import com.coliving.platform.common.auth.ResidentGuard;
import com.coliving.platform.faq.dto.FaqDocumentListResponseDTO;
import com.coliving.platform.faq.dto.FaqDocumentUploadResponseDTO;
import com.coliving.platform.faq.dto.FaqIndexJobDTO;
import com.coliving.platform.faq.dto.FaqReindexRequestDTO;
import com.coliving.platform.faq.entity.FaqDocumentEntity;
import com.coliving.platform.faq.entity.FaqScope;
import com.coliving.platform.faq.service.AdminFaqDocumentService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** FAQ 문서 관리 — 관리자 (API 7-2 ~ 7-7) */
@RestController
@RequestMapping("/community-service/admin/faq-documents")
@RequiredArgsConstructor
public class AdminFaqDocumentController {

	private final AdminFaqDocumentService adminFaqDocumentService;
	private final ResidentGuard residentGuard;

	/** 7-2 문서 목록 · 반영 상태 */
	@GetMapping
	public FaqDocumentListResponseDTO getDocuments(@RequestHeader("X-User") Long userId,
			@RequestParam(required = false) FaqScope scope, @RequestParam(required = false) Long buildingId) {
		residentGuard.requireAdmin(userId);
		return adminFaqDocumentService.getDocuments(scope, buildingId);
	}

	/** 7-3 업로드 (multipart/form-data) */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<FaqDocumentUploadResponseDTO> upload(@RequestHeader("X-User") Long userId,
			@RequestParam("file") MultipartFile file, @RequestParam("title") String title,
			@RequestParam("scope") FaqScope scope, @RequestParam(value = "buildingId", required = false) Long buildingId) {
		residentGuard.requireAdmin(userId);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(adminFaqDocumentService.upload(userId, file, title, scope, buildingId));
	}

	/** 7-4 삭제 */
	@DeleteMapping("/{documentId}")
	public ResponseEntity<Void> delete(@RequestHeader("X-User") Long userId, @PathVariable Long documentId) {
		residentGuard.requireAdmin(userId);
		adminFaqDocumentService.delete(documentId);
		return ResponseEntity.noContent().build();
	}

	/** 7-5 원본 다운로드 */
	@GetMapping("/{documentId}/file")
	public ResponseEntity<byte[]> download(@RequestHeader("X-User") Long userId, @PathVariable Long documentId) {
		residentGuard.requireAdmin(userId);
		FaqDocumentEntity document = adminFaqDocumentService.getFile(documentId);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(document.getFileName(), StandardCharsets.UTF_8).build().toString())
				.contentType(MediaType.parseMediaType(document.getFileType().contentType()))
				.contentLength(document.getFileData().length)
				.body(document.getFileData());
	}

	/** 7-6 업데이트 반영 시작 — Body 생략 가능 (대기·실패 문서 전체) */
	@PostMapping("/reindex")
	public ResponseEntity<FaqIndexJobDTO> reindex(@RequestHeader("X-User") Long userId,
			@RequestBody(required = false) FaqReindexRequestDTO request) {
		residentGuard.requireAdmin(userId);
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(adminFaqDocumentService.startReindex(userId, request != null ? request.documentIds() : null));
	}

	/** 7-7 반영 진행 상태 */
	@GetMapping("/reindex/{jobId}")
	public FaqIndexJobDTO getJob(@RequestHeader("X-User") Long userId, @PathVariable Long jobId) {
		residentGuard.requireAdmin(userId);
		return adminFaqDocumentService.getJob(jobId);
	}
}
