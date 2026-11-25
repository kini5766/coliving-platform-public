
package com.coliving.reservation.controller;

import com.coliving.reservation.entity.ResourceInquiry;
import com.coliving.reservation.service.ResourceInquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reservation-service/admin/inquiries")
public class AdminResourceInquiryController {

    private final ResourceInquiryService resourceInquiryService;

    // ==========================================
    // 1. 관리자 전체 문의 목록 조회
    // ==========================================
    @GetMapping
    public List<ResourceInquiry> getAllInquiries() {

        return resourceInquiryService.getAllInquiries();
    }

    // ==========================================
    // 2. 관리자 문의 답변 등록
    // ==========================================
    @PatchMapping("/{id}/reply")
    public ResourceInquiry replyToInquiry(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        return resourceInquiryService.replyToInquiry(
                id,
                request.get("reply")
        );
    }
}
