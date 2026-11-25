package com.coliving.platform.common.exception;

/**
 * 서명 이미지·서명 PDF의 생성·저장·조회 실패 → 500
 *
 * @author 한성현
 */
public class ContractFileException extends RuntimeException {
    public ContractFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
