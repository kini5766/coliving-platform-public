package com.coliving.platform.common.exception;

/**
 * 신청번호에 해당하는 신청서가 없음 → 404
 *
 * @author 한성현
 */
public class ApplicationNotFoundException extends RuntimeException {
    public ApplicationNotFoundException(String message) {
        super(message);
    }
}
