package com.pgpayment.support.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * PG 에러코드 체계
 *
 * 장점: 에러코드 중앙화 → 가맹점 연동 문서와 1:1 매핑, 운영팀 대응 용이
 * 단점: 카드사 추가 시 enum 수정 필요 → 개방-폐쇄 원칙 위반 여지
 */
@Getter
@RequiredArgsConstructor
public enum PgErrorCode {

    // 성공
    SUCCESS("0000", "성공"),

    // 결제 요청 오류 (1xxx)
    INVALID_AMOUNT("1001", "결제금액 오류"),
    INVALID_ORDER_ID("1002", "주문번호 오류"),
    INVALID_CARD_NUMBER("1003", "카드번호 오류"),
    MISSING_MERCHANT("1004", "가맹점 정보 없음"),

    // 중복/멱등성 오류 (2xxx)
    DUPLICATE_PAYMENT("2001", "중복 결제 요청"),
    IDEMPOTENCY_KEY_MISMATCH("2002", "멱등성 키 불일치"),

    // 상태 전이 오류 (3xxx)
    INVALID_STATUS_TRANSITION("3001", "잘못된 결제 상태 전이"),
    ALREADY_CANCELLED("3002", "이미 취소된 결제"),

    // 카드사 오류 (4xxx)
    CARD_COMPANY_TIMEOUT("4001", "카드사 응답 시간 초과"),
    CARD_COMPANY_ERROR("4002", "카드사 오류"),
    CIRCUIT_BREAKER_OPEN("4003", "카드사 서킷브레이커 개방"),
    INSUFFICIENT_BALANCE("4004", "한도 초과"),

    // 인증 오류 (5xxx)
    UNAUTHORIZED("5001", "인증 실패"),
    INVALID_API_KEY("5002", "유효하지 않은 API Key"),
    EXPIRED_TOKEN("5003", "만료된 토큰"),

    // 시스템 오류 (9xxx)
    INTERNAL_ERROR("9999", "시스템 오류");

    private final String code;
    private final String message;
}
