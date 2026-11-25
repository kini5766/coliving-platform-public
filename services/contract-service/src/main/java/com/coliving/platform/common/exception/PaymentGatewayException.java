package com.coliving.platform.common.exception;

/**
 * 카카오페이 호출 실패(장애·인증 설정 오류·타임아웃) 또는 결제 결과 확인 불가 → 503
 *
 * @author 한성현
 */
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
