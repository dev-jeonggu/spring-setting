package com.pgpayment.core.event;

import com.pgpayment.core.domain.Payment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 결제 승인 도메인 이벤트
 *
 * @EventListener / ApplicationEventPublisher 학습 포인트
 *
 * 장점:
 *   - PaymentService가 WebhookService, AuditService 등에 직접 의존하지 않음
 *   - 후속 처리 추가/제거 시 PaymentService 코드 무수정
 *   - @TransactionalEventListener 사용 시 커밋 후 이벤트 발행 → 정합성 보장
 *
 * 단점:
 *   - 흐름 추적이 어려움 (이벤트 → 어디서 처리?를 IDE에서 찾기 힘듦)
 *   - 동기 이벤트는 리스너 실패 시 전체 트랜잭션 롤백될 수 있음
 *   - 비동기 이벤트는 실패 처리가 별도로 필요
 */
@Getter
public class PaymentApprovedEvent extends ApplicationEvent {

    private final Payment payment;
    private final String traceId;

    public PaymentApprovedEvent(Object source, Payment payment, String traceId) {
        super(source);
        this.payment = payment;
        this.traceId = traceId;
    }
}
