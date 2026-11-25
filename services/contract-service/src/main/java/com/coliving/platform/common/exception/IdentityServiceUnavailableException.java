package com.coliving.platform.common.exception;

/**
 * 포트원 API 호출 실패 (네트워크·인증·서버 오류) → CustomControllerAdvice에서 503
 *
 * @author 한성현
 */
public class IdentityServiceUnavailableException extends RuntimeException {
    public IdentityServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
