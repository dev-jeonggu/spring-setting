package com.pgpayment.core.domain;

/**
 * 결제 상태 Enum
 *
 * State Machine 패턴 학습 포인트:
 * 허용된 전이만 가능하도록 StatusTransitionValidator가 검증
 *
 * READY → PENDING → SUCCESS → REFUND_REQUESTED → REFUNDED
 *                 ↘ FAILED
 */
public enum PaymentStatus {
    READY,
    PENDING,
    SUCCESS,
    FAILED,
    REFUND_REQUESTED,
    REFUNDED
}
