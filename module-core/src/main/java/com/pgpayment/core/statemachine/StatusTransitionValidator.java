package com.pgpayment.core.statemachine;

import com.pgpayment.core.domain.PaymentStatus;
import com.pgpayment.support.exception.PgErrorCode;
import com.pgpayment.support.exception.PgException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * State Machine — 결제 상태 전이 검증
 *
 * 장점:
 *   - 허용된 전이만 가능 → 데이터 정합성 보장
 *   - 비즈니스 규칙을 코드로 명시화 → 문서 역할 겸임
 *
 * 단점:
 *   - 상태가 많아지면 전이 테이블 관리가 복잡해짐
 *   - Spring State Machine 라이브러리 도입 vs 직접 구현 트레이드오프
 *     (직접 구현이 단순한 경우 더 이해하기 쉬움)
 *
 * 면접 포인트:
 *   Q. 잘못된 상태 전이를 어떻게 막았나?
 *   A. EnumMap으로 허용 전이 테이블 정의, 서비스 레이어 진입 시 검증
 */
@Component
public class StatusTransitionValidator {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(PaymentStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(PaymentStatus.READY,             Set.of(PaymentStatus.PENDING));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PENDING,           Set.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.SUCCESS,           Set.of(PaymentStatus.REFUND_REQUESTED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.FAILED,            Set.of());
        ALLOWED_TRANSITIONS.put(PaymentStatus.REFUND_REQUESTED,  Set.of(PaymentStatus.REFUNDED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.REFUNDED,          Set.of());
    }

    public void validate(PaymentStatus current, PaymentStatus next) {
        Set<PaymentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new PgException(PgErrorCode.INVALID_STATUS_TRANSITION,
                    String.format("%s → %s 전이는 허용되지 않습니다", current, next));
        }
    }
}
