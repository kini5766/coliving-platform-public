package com.coliving.platform.common.exception;

/**
 * 문서 서비스가 꺼져 있거나 응답 시간(deadline)을 넘겼을 때 → 503
 *
 * @author 한성현
 */
public class DocumentServiceUnavailableException extends RuntimeException {
    public DocumentServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}