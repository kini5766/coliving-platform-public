package com.coliving.platform.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 계약 서비스 공통 예외 핸들러
 * @author 한성현
 */
@RestControllerAdvice
public class CustomControllerAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException e) {
        e.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body("접근 권한이 없습니다.");
    }


    // ---- 신청서(contract_application) 관련 ----

    // X-User 헤더 없음 = gateway를 거쳤는데 로그인하지 않은 요청 (gateway가 헤더를 지움)
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(
            MissingRequestHeaderException e, HttpServletRequest request) {
        if ("X-User".equals(e.getHeaderName())) {
            return buildError(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.", request.getRequestURI());
        }
        return buildError(HttpStatus.BAD_REQUEST, "필수 헤더가 없습니다: " + e.getHeaderName(), request.getRequestURI());
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleApplicationNotFound(
            ApplicationNotFoundException e, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, e.getMessage(), request.getRequestURI());
    }

    // 남의 신청서 조회·제출 시도
    @ExceptionHandler(ApplicationAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleApplicationAccessDenied(
            ApplicationAccessDeniedException e, HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, e.getMessage(), request.getRequestURI());
    }

    // 엔티티·서비스의 값 검증 실패 (계약 기간, 금액, 약관 미동의 등)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, e.getMessage(), request.getRequestURI());
    }

    // 지금 상태에서 할 수 없는 요청 (DRAFT가 아닌데 제출, 작성 기한 지남 등)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException e, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, e.getMessage(), request.getRequestURI());
    }

    // @Version 충돌: 같은 신청서를 두 곳에서 동시에 수정
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT,
                "다른 곳에서 신청서가 먼저 변경됐습니다. 새로고침 후 다시 시도해주세요.", request.getRequestURI());
    }

    // DB 제약 위반. 23P01 = no_overlap_booking(같은 호실·겹치는 기간)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            DataIntegrityViolationException e, HttpServletRequest request) {
        e.printStackTrace();
        if ("23P01".equals(sqlState(e))) {
            return buildError(HttpStatus.CONFLICT,
                    "선택한 기간에 이미 진행 중인 신청이 있어요. 다른 입주일을 선택해주세요.", request.getRequestURI());
        }
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "신청서를 저장하지 못했습니다.", request.getRequestURI());
    }

    // 예외 안쪽(cause)을 따라가며 PostgreSQL SQLState를 찾는다
    private String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    // 위에서 처리하지 않은 예상 밖의 RuntimeException = 서버 쪽 오류 → 500 (JSON)
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException e, HttpServletRequest request) {
        e.printStackTrace();
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request.getRequestURI());
    }

    // 요청 본문(JSON) 형식 오류. RuntimeException 하위라서 따로 받지 않으면 500이 된다 → 400 유지
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다.", request.getRequestURI());
    }

    // 경로·쿼리 값의 타입이 안 맞음 (예: 숫자 자리에 문자). 마찬가지로 400 유지
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
            Exception e, HttpServletRequest request) {

        e.printStackTrace();
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request.getRequestURI());
    }

    // ---- 본인인증(포트원) 관련 ----

    // 인증 미완료·실패·신청자와 불일치
    @ExceptionHandler(IdentityVerificationException.class)
    public ResponseEntity<Map<String, Object>> handleIdentityVerification(
            IdentityVerificationException e, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, e.getMessage(), request.getRequestURI());
    }

    // 포트원 API 호출 실패
    @ExceptionHandler(IdentityServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleIdentityServiceUnavailable(
            IdentityServiceUnavailableException e, HttpServletRequest request) {
        e.printStackTrace();
        return buildError(HttpStatus.SERVICE_UNAVAILABLE,
                "본인인증 결과를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.", request.getRequestURI());
    }

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

    // ---- 결제(카카오페이) 관련 ----

    // 카카오페이가 요청을 거절함 (잘못된 pg_token, 결제 실패 등)
    @ExceptionHandler(PaymentRejectedException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentRejected(
            PaymentRejectedException e, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, e.getMessage(), request.getRequestURI());
    }

    // 카카오페이 장애·설정 오류, 또는 결제 결과를 확인하지 못함
    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentGateway(
            PaymentGatewayException e, HttpServletRequest request) {
        e.printStackTrace();
        return buildError(HttpStatus.SERVICE_UNAVAILABLE,
                "결제 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해주세요.", request.getRequestURI());
    }

    // ---- 서명 이미지·서명 PDF 파일 관련 ----

    @ExceptionHandler(ContractFileException.class)
    public ResponseEntity<Map<String, Object>> handleContractFile(
            ContractFileException e, HttpServletRequest request) {
        e.printStackTrace();
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                "계약서 파일을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.", request.getRequestURI());
    }

    // ---- 문서 서비스(gRPC) 호출 관련 ----

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentNotFound(
            DocumentNotFoundException e, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, e.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DocumentServiceException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentService(
            DocumentServiceException e, HttpServletRequest request) {
        e.printStackTrace();
        return buildError(HttpStatus.BAD_GATEWAY,
                "계약서 양식을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.", request.getRequestURI());
    }

    @ExceptionHandler(DocumentServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentServiceUnavailable(
            DocumentServiceUnavailableException e, HttpServletRequest request) {
        e.printStackTrace();
        return buildError(HttpStatus.SERVICE_UNAVAILABLE,
                "연결된 서비스가 응답하지 않습니다. 잠시 후 다시 시도해주세요.", request.getRequestURI());
    }
}
