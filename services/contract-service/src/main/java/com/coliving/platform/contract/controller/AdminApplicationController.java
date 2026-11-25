package com.coliving.platform.contract.controller;

import com.coliving.platform.contract.dto.admin.AdminApplicationDetailResponse;
import com.coliving.platform.contract.dto.admin.AdminApplicationListItemResponse;
import com.coliving.platform.contract.dto.admin.ApplicationApproveResponse;
import com.coliving.platform.contract.dto.admin.ApplicationRejectRequest;
import com.coliving.platform.contract.dto.admin.ApplicationRejectResponse;
import com.coliving.platform.contract.enums.ContractStatus;
import com.coliving.platform.contract.service.AdminApplicationQueryService;
import com.coliving.platform.contract.service.ApplicationApprovalService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 신청 심사 API. 경로: /contract-service/admin/applications/**
 *
 * 관리자 화면(admin-web) → Gateway → 여기 (문서 서비스를 거치지 않는다)
 * - 신청서·계약서 데이터의 주인이 계약 서비스이므로 관리자가 직접 조회·승인·반려한다
 * - 관리자 권한 확인은 Gateway가 한다 (/*\/admin/** → ROLE_ADMIN)
 * - 처리한 관리자 ID는 Gateway가 넣어 주는 X-User 헤더로만 받는다 (body로 받지 않는다. 없으면 401)
 * - 신청 상태 변경과 계약서(contract_document) 저장은 계약 서비스가 한다
 *
 * @author 한성현
 */
@RestController
@RequestMapping("/admin/applications")
public class AdminApplicationController {

    /* Gateway UserIdHeaderWebFilter.SUBJECT_HEADER와 같아야 한다*/
    private static final String USER_HEADER = "X-User";

    private final ApplicationApprovalService approvalService;
    private final AdminApplicationQueryService queryService;

    public AdminApplicationController(ApplicationApprovalService approvalService,
                                      AdminApplicationQueryService queryService) {
        this.approvalService = approvalService;
        this.queryService = queryService;
    }

    /**
     * 관리자 계약서 목록.
     *
     * @param keyword  신청자 이름·건물명·호실·신청번호 부분 일치 (예: 망원). 없으면 전체
     * @param statuses 상태 값 여러 개 (예: SUBMITTED,APPROVED). 없으면 DRAFT 빼고 전부, 없는 값이면 400
     */
    @GetMapping
    public List<AdminApplicationListItemResponse> list(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "statuses", required = false) List<ContractStatus> statuses) {

        return queryService.findAll(keyword, statuses);
    }

    /**
     * 관리자 계약서 상세.
     *
     * @param applicationId contract_application.id (승인·반려와 같은 번호, 신청번호 아님)
     */
    @GetMapping("/{applicationId}")
    public AdminApplicationDetailResponse detail(@PathVariable("applicationId") Long applicationId) {
        return queryService.find(applicationId);
    }

    /**
     * 신청 승인. SUBMITTED → APPROVED, 결제 기한(+1일)이 시작된다.
     * 계약서는 제출 때 이미 만들어져 있어서 상태만 바꾼다. body 없음.
     *
     * @param adminId       X-User 헤더. Gateway가 넣는 처리 관리자 ID
     * @param applicationId contract_application.id
     */
    @PostMapping("/{applicationId}/approve")
    public ApplicationApproveResponse approve(
            @RequestHeader(USER_HEADER) Long adminId,
            @PathVariable("applicationId") Long applicationId) {

        return approvalService.approve(adminId, applicationId);
    }

    /**
     * 신청 반려. SUBMITTED → REJECTED, 사유를 저장한다.
     *
     * @param adminId       X-User 헤더. Gateway가 넣는 처리 관리자 ID
     * @param applicationId contract_application.id
     */
    @PostMapping("/{applicationId}/reject")
    public ApplicationRejectResponse reject(
            @RequestHeader(USER_HEADER) Long adminId,
            @PathVariable("applicationId") Long applicationId,
            @RequestBody ApplicationRejectRequest req) {

        return approvalService.reject(adminId, applicationId, req.reason());
    }
}
