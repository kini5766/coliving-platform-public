package com.coliving.platform.contract.controller;

import com.coliving.platform.contract.dto.application.ApplicationDetailResponse;
import com.coliving.platform.contract.dto.application.ApplicationDraftRequest;
import com.coliving.platform.contract.dto.application.ApplicationDraftResponse;
import com.coliving.platform.contract.dto.application.ApplicationListItemResponse;
import com.coliving.platform.contract.dto.application.ApplicationSubmitRequest;
import com.coliving.platform.contract.dto.application.ApplicationSubmitResponse;
import com.coliving.platform.contract.dto.application.ApplyFormResponse;
import com.coliving.platform.contract.service.ApplicationService;
import com.coliving.platform.contract.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 신청 1단계 API. gateway 경로: /contract-service/apply/applications/**
 *
 * 회원 번호는 X-User 헤더로만 받는다.
 * - gateway가 로그인 토큰을 검증한 뒤 토큰의 회원 ID를 X-User에 넣어준다
 * - 로그인하지 않은 요청이 X-User를 직접 넣어 보내면 gateway가 지운다 → 위조 불가
 * - 헤더가 없으면 CustomControllerAdvice가 401로 응답
 * ⚠ contract-service 포트(8089)로 직접 부르면 X-User를 마음대로 넣을 수 있다. 개발 중에만 직접 호출할 것
 *
 * @author 한성현
 */
@RestController
@RequestMapping("/apply/applications")
public class ApplicationController {

    private static final String USER_HEADER = "X-User";   // api-gateway UserIdHeaderWebFilter.SUBJECT_HEADER와 같아야 함

    private final ApplicationService applicationService;
    private final PaymentService paymentService;

    public ApplicationController(ApplicationService applicationService, PaymentService paymentService) {
        this.applicationService = applicationService;
        this.paymentService = paymentService;
    }

    /**
     * 입주 전 취소. 결제 완료 계약을 입주일 전날까지 취소하고 전액 환불한다.
     * 응답은 바뀐 신청서 상세: status REFUNDED = 환불 완료,
     * MOVE_IN_CANCEL_REQUESTED = 카카오 응답 지연으로 환불 처리 중 (자동으로 다시 시도).
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @PostMapping("/{applicationNo}/move-in-cancel")
    public ApplicationDetailResponse cancelBeforeMoveIn(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo) {

        paymentService.cancelBeforeMoveIn(userId, applicationNo);
        return applicationService.getDetail(userId, applicationNo);
    }

    /**
     * 1-1 "다음 단계": 작성 중(DRAFT) 신청서를 만든다 (201).
     * 같은 호실의 DRAFT가 이미 있으면 그걸 고쳐서 돌려준다.
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @PostMapping
    public ResponseEntity<ApplicationDraftResponse> createDraft(
            @RequestHeader(USER_HEADER) Long userId,
            @RequestBody ApplicationDraftRequest req) {

        ApplicationDraftResponse res = applicationService.createDraft(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);   // 201
    }

    /**
     * 나의 신청 목록. 제출한 내 신청 전부 (작성 중 DRAFT 제외, 최근 신청 순).
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @GetMapping
    public List<ApplicationListItemResponse> getMyApplications(
            @RequestHeader(USER_HEADER) Long userId) {

        return applicationService.getMyApplications(userId);
    }

    /**
     * 신청서 1건 상세 (접수 완료·나의 신청 상세·계약 완료 화면).
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @GetMapping("/{applicationNo}")
    public ApplicationDetailResponse getDetail(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo) {

        return applicationService.getDetail(userId, applicationNo);
    }

    /**
     * 접수 완료 화면 "취소하기". SUBMITTED → CANCELED, 응답은 바뀐 신청서 상세.
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @PostMapping("/{applicationNo}/cancel")
    public ApplicationDetailResponse cancel(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo) {

        return applicationService.cancel(userId, applicationNo);
    }

    /**
     * 1-2 신청자 정보 화면에 보여줄 신청서 값 (DRAFT만).
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @GetMapping("/{applicationNo}/form")
    public ApplyFormResponse getForm(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo) {

        return applicationService.getForm(userId, applicationNo);
    }

    /**
     * 1-2 "신청서 제출". DRAFT → SUBMITTED, 이때 금액이 고정되고 계약서가 만들어진다.
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @PostMapping("/{applicationNo}/submit")
    public ApplicationSubmitResponse submit(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo,
            @RequestBody ApplicationSubmitRequest req) {

        return applicationService.submit(userId, applicationNo, req);
    }
}
