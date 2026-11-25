package com.coliving.platform.contract.service;

import java.time.Instant;

/**
 * 카카오페이 온라인 단건 결제 창구.
 * - 구현: KakaoPayRestClient
 *
 * 실패는 PaymentRejectedException(카카오가 거절) / PaymentGatewayException(장애·결과 불명)으로 던진다.
 *
 * @author 한성현
 */
public interface KakaoPayClient {

    Ready ready(ReadyCommand command);

    Approved approve(String tid, String orderId, String userId, String pgToken);

    /** 주문 조회. approve 응답을 못 받았을 때 실제 결과를 확인한다 */
    Order order(String tid);

    /** 원결제 전액 취소 */
    void cancel(String tid, long amount);

    record ReadyCommand(String orderId, String userId, String itemName, long amount,
                        String approvalUrl, String cancelUrl, String failUrl) {}

    record Ready(String tid, String redirectPcUrl, String redirectMobileUrl) {}

    record Approved(String paymentMethod, Instant approvedAt) {}

    record Order(String status, Approved approved) {
        public boolean paid() {
            return "SUCCESS_PAYMENT".equals(status);
        }

        /** 사용자가 더 진행할 수 없는 상태 */
        public boolean failed() {
            return "QUIT_PAYMENT".equals(status) || (status != null && status.startsWith("FAIL_"));
        }
    }
}
