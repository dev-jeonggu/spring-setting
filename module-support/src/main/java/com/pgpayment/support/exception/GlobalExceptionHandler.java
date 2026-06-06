package com.pgpayment.support.exception;

import com.pgpayment.support.mdcfilter.TraceIdHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — @ControllerAdvice 학습 포인트
 *
 * 장점:
 *   - 예외 처리 로직을 Controller에서 분리 → 단일 책임 원칙 준수
 *   - PG 에러코드 체계로 변환 → 가맹점 연동 표준화
 *   - traceId 자동 포함 → 운영 추적 가능
 *
 * 단점:
 *   - 예외 타입이 많아지면 Handler 클래스가 비대해짐
 *   - 모든 예외를 여기서 처리하면 도메인 경계가 희미해질 수 있음
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PgException.class)
    public ResponseEntity<Map<String, Object>> handlePgException(PgException e) {
        log.error("[{}] PgException: {} - {}", TraceIdHolder.get(), e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity
                .status(httpStatus(e.getErrorCode()))
                .body(errorBody(e.getErrorCode(), e.getMessage()));
    }

    // @Validated 검증 실패 시 발생 — Bean Validation 학습 포인트
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("[{}] Validation failed: {}", TraceIdHolder.get(), detail);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorBody(PgErrorCode.INVALID_AMOUNT, detail));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("[{}] Unexpected error", TraceIdHolder.get(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(PgErrorCode.INTERNAL_ERROR, e.getMessage()));
    }

    private Map<String, Object> errorBody(PgErrorCode code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code.getCode());
        body.put("message", message);
        body.put("traceId", TraceIdHolder.get());
        return body;
    }

    private HttpStatus httpStatus(PgErrorCode code) {
        return switch (code.getCode().charAt(0)) {
            case '1', '2', '3' -> HttpStatus.BAD_REQUEST;
            case '4' -> HttpStatus.BAD_GATEWAY;
            case '5' -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
