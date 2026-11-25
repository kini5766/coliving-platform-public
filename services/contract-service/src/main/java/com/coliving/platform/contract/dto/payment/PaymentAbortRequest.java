package com.coliving.platform.contract.dto.payment;

/**
 * [3 결제 중단] POST /contract-service/apply/applications/{applicationNo}/payment/abort
 *
 * 카카오페이 결제창에서 취소하거나 실패해 cancel_url / fail_url로 돌아왔을 때 프론트가 보낸다.
 * result: "cancel" (사용자 취소) / "fail" (결제 실패)
 *
 * @author 한성현
 */
public record PaymentAbortRequest(
        String orderId,
        String result
) {}
