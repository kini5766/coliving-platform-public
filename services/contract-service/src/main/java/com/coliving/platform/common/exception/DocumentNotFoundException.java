package com.coliving.platform.common.exception;

/**
 * 양식이 없을 때(문서 서비스 NOT_FOUND) → CustomControllerAdvice에서 404로 응답
 *
 * @author 한성현
 */
public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String message) {
        super(message);
    }
}