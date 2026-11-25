package com.coliving.platform.common.exception;

/**
 * 문서 서비스가 NOT_FOUND·UNAVAILABLE 외의 gRPC 에러를 돌려줬거나 응답이 비정상일 때 → 502
 *
 * @author 한성현
 */
public class DocumentServiceException extends RuntimeException {
    public DocumentServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}