package com.coliving.platform.common.exception;

/**
 * 카카오페이가 요청을 거절함 (잘못된 pg_token, 이미 처리된 거래 등) → 400
 *
 * @author 한성현
 */
public class PaymentRejectedException extends RuntimeException {
    public PaymentRejectedException(String message) {
        super(message);
    }
}
