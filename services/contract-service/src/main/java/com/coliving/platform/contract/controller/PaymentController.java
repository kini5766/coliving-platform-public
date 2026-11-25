package com.coliving.platform.contract.controller;

import com.coliving.platform.contract.dto.payment.KakaoPayApproveRequest;
import com.coliving.platform.contract.dto.payment.PaymentAbortRequest;
import com.coliving.platform.contract.dto.payment.PaymentInfoResponse;
import com.coliving.platform.contract.dto.payment.PaymentReadyRequest;
import com.coliving.platform.contract.dto.payment.PaymentReadyResponse;
import com.coliving.platform.contract.dto.payment.PaymentResultResponse;
import com.coliving.platform.contract.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 3단계 결제 API. gateway 경로: /contract-service/apply/applications/{applicationNo}/payment/**
 * 회원 번호는 X-User 헤더로만 받는다 (ApplicationController 설명 참고).
 *
 * @author 한성현
 */
@RestController
@RequestMapping("/apply/applications/{applicationNo}/payment")
public class PaymentController {

    private static final String USER_HEADER = "X-User";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 결제 화면 진입. 결제 금액과 결제 기한을 내려준다.
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @GetMapping
    public PaymentInfoResponse getInfo(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo) {

        return paymentService.getInfo(userId, applicationNo);
    }

    /**
     * 결제하기. 카카오페이 결제 준비 후 결제창 주소를 돌려준다.
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @PostMapping("/ready")
    public PaymentReadyResponse ready(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo,
            @RequestBody PaymentReadyRequest req) {

        return paymentService.ready(userId, applicationNo, req);
    }

    /**
     * 카카오페이 결제창에서 돌아온 뒤 결제 승인.
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @PostMapping("/kakaopay/approve")
    public PaymentResultResponse approveKakaoPay(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo,
            @RequestBody KakaoPayApproveRequest req) {

        return paymentService.approveKakaoPay(userId, applicationNo, req);
    }

    /**
     * 결제창에서 취소·실패로 돌아왔을 때 결제 시도를 닫는다 (204).
     *
     * @param userId X-User 헤더. Gateway가 로그인 토큰에서 꺼내 넣는 회원 ID
     */
    @PostMapping("/abort")
    public ResponseEntity<Void> abort(
            @RequestHeader(USER_HEADER) Long userId,
            @PathVariable("applicationNo") String applicationNo,
            @RequestBody PaymentAbortRequest req) {

        paymentService.abort(userId, applicationNo, req);
        return ResponseEntity.noContent().build();
    }
}
