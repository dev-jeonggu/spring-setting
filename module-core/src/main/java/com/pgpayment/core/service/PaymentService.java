package com.pgpayment.core.service;

import com.pgpayment.core.domain.Merchant;
import com.pgpayment.core.domain.Payment;
import com.pgpayment.core.domain.PaymentStatus;
import com.pgpayment.core.event.PaymentApprovedEvent;
import com.pgpayment.core.idempotency.Idempotent;
import com.pgpayment.core.repository.PaymentRepository;
import com.pgpayment.core.statemachine.StatusTransitionValidator;
import com.pgpayment.support.audit.PaymentAuditLog;
import com.pgpayment.support.audit.PaymentAuditLogRepository;
import com.pgpayment.support.exception.PgErrorCode;
import com.pgpayment.support.exception.PgException;
import com.pgpayment.support.mdcfilter.TraceIdHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 결제 서비스 — @Transactional 학습 포인트
 *
 * @Transactional(readOnly = true): 읽기 전용 최적화 (Hibernate flush 스킵, DB 부하 감소)
 * @Transactional: 쓰기 작업 — 예외 발생 시 자동 롤백
 *
 * REQUIRES_NEW 포인트:
 *   실패 로그는 결제 실패와 별개 트랜잭션으로 저장해야 함
 *   결제가 롤백되어도 실패 이력은 남아야 하기 때문
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAuditLogRepository auditLogRepository;
    private final StatusTransitionValidator transitionValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final MerchantCacheService merchantCacheService;

    @Transactional
    @Idempotent(keyParam = "idempotencyKey") // AOP 멱등성 검증
    public Payment approve(String merchantId, String orderId, Long amount,
                           String cardNumber, String idempotencyKey) {
        Merchant merchant = merchantCacheService.findById(merchantId);
        if (!merchant.getActive()) {
            throw new PgException(PgErrorCode.MISSING_MERCHANT, "비활성 가맹점: " + merchantId);
        }

        String paymentId = UUID.randomUUID().toString();
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .orderId(orderId)
                .merchantId(merchantId)
                .amount(amount)
                .cardNumber(cardNumber)
                .build();

        // READY → PENDING
        transitionValidator.validate(payment.getStatus(), PaymentStatus.PENDING);
        payment.transition(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        // 카드사 승인 (infra 레이어에서 실제 호출 — 여기서는 성공으로 가정)
        // PENDING → SUCCESS
        transitionValidator.validate(payment.getStatus(), PaymentStatus.SUCCESS);
        payment.transition(PaymentStatus.SUCCESS);
        paymentRepository.save(payment); // 상태 변경을 명시적으로 저장

        // Audit Trail 기록
        auditLogRepository.save(PaymentAuditLog.builder()
                .paymentId(paymentId)
                .previousStatus("PENDING")
                .newStatus("SUCCESS")
                .traceId(TraceIdHolder.get())
                .changedBy(merchantId)
                .build());

        // 도메인 이벤트 발행 → WebhookListener, NotificationListener가 처리
        eventPublisher.publishEvent(new PaymentApprovedEvent(this, payment, TraceIdHolder.get()));

        log.info("[{}] 결제 승인 완료: paymentId={}, amount={}", TraceIdHolder.get(), paymentId, amount);
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment findById(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PgException(PgErrorCode.INTERNAL_ERROR, "결제 없음: " + paymentId));
    }
}
