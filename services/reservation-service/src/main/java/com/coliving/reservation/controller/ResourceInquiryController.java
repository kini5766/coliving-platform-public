
package com.coliving.reservation.controller;

import com.coliving.reservation.entity.ResourceInquiry;
import com.coliving.reservation.service.ResourceInquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reservation-service/inquiries")
public class ResourceInquiryController {

    private final ResourceInquiryService resourceInquiryService;

    // 1. 관리자 문의 등록
    @PostMapping
    public ResourceInquiry createInquiry(
            @RequestHeader("X-User") Long userId,
            @RequestBody Map<String, Object> request) {

        Long reservationId = Long.valueOf(
                request.get("reservationId").toString());

        String inquiryType = (String) request.get("inquiryType");
        String content = (String) request.get("content");

        return resourceInquiryService.createInquiry(
                userId, reservationId, inquiryType, content);
    }

    // 2. 내 문의 목록 조회
    @GetMapping("/my")
    public List<ResourceInquiry> getMyInquiries(
            @RequestHeader("X-User") Long userId) {

        return resourceInquiryService.getMyInquiries(userId);
    }

    // 3. 사용자 문의 삭제
    @DeleteMapping("/{id}")
    public void deleteInquiry(
            @PathVariable Long id,
            @RequestHeader("X-User") Long userId) {

        resourceInquiryService.deleteInquiry(id, userId);
    }
}
