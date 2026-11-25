package com.coliving.platform.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 컨트롤러에서 나온 예외를 HTTP 응답으로 바꾼다.
 *
 * @author 조휘재
 * 작성일: 2026-10-16
 */
@RestControllerAdvice
public class CustomControllerAdvice {

    /** 권한 없음은 403 과 안내 문구를 돌려준다. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException e) {
        e.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body("접근 권한이 없습니다.");
    }

    /** 런타임 예외는 400 과 안내 문구를 돌려준다. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        ex.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("잘못된 요청입니다.");
    }

    /** 그 밖의 예외는 500 과 상태, 경로, 시각을 담은 JSON 을 돌려준다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
            Exception e, HttpServletRequest request) {

        e.printStackTrace();
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request.getRequestURI());
    }

    /** 에러 JSON 본문을 만든다. */
    private ResponseEntity<Map<String, Object>> buildError(
            HttpStatus status, String message, String path) {

        Map<String, Object> body = Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", path,
                "timestamp", LocalDateTime.now().toString()
        );

        return ResponseEntity.status(status).body(body);
    }
}