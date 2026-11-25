package com.coliving.platform.common.exception;

/**
 * 본인인증 결과가 유효하지 않음 (미완료·실패·신청자와 불일치) → CustomControllerAdvice에서 400
 *
 * @author 한성현
 */
public class IdentityVerificationException extends RuntimeException {
    public IdentityVerificationException(String message) {
        super(message);
    }
}
