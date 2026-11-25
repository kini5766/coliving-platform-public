package com.coliving.document.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * ResponseStatusException을 { "message": "..." } 모양으로 내려준다.
 * 관리자 화면(formApi.js)과 계약 서비스가 이 message를 읽어 사용자에게 이유를 보여준다.
 * (스프링 기본 오류 응답은 message를 숨긴다)
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handle(ResponseStatusException e) {
        String message = e.getReason() == null ? "" : e.getReason();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }
}