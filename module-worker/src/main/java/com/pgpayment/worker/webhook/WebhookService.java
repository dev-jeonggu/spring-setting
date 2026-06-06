package com.pgpayment.worker.webhook;

import com.pgpayment.core.domain.Payment;
import com.pgpayment.core.event.PaymentApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * 웹훅 비동기 발송 서비스 — @Async + @EventListener 학습 포인트 (모듈 4, 7)
 *
 * @TransactionalEventListener(phase = AFTER_COMMIT):
 *   트랜잭션 커밋 이후에만 웹훅 발송
 *   → 결제 DB 저장 전에 웹훅 발송되어 가맹점이 미존재 결제를 조회하는 상황 방지
 *
 * @Async("webhookExecutor"):
 *   웹훅 발송을 별도 쓰레드 풀에서 처리
 *   → 결제 응답이 웹훅 발송 완료를 기다리지 않음
 *
 * 장점:
 *   - 결제 응답 속도와 웹훅 발송이 독립적
 *   - 웹훅 실패가 결제 트랜잭션에 영향 없음
 *   - 재시도(DLQ) 패턴 적용 가능
 *
 * 단점:
 *   - 웹훅 발송 실패 시 별도 추적/재시도 메커니즘 필요
 *   - 트랜잭션 롤백 후 AFTER_COMMIT 이벤트는 발행 안 됨 → AFTER_ROLLBACK 별도 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebClient.Builder webClientBuilder;
    private final WebhookRetryRepository webhookRetryRepository;

    @Async("webhookExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentApproved(PaymentApprovedEvent event) {
        Payment payment = event.getPayment();
        log.info("[{}] 웹훅 발송 시작: paymentId={}", event.getTraceId(), payment.getPaymentId());

        sendWebhook(payment.getMerchantId(), payment.getPaymentId(), "APPROVED", event.getTraceId());
    }

    private void sendWebhook(String merchantId, String paymentId, String status, String traceId) {
        try {
            // 실제 구현에서는 가맹점 webhookUrl을 캐시에서 조회
            String webhookUrl = "https://merchant.example.com/webhook";

            webClientBuilder.build()
                    .post()
                    .uri(webhookUrl)
                    .bodyValue(Map.of(
                            "paymentId", paymentId,
                            "status", status,
                            "traceId", traceId
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("[{}] 웹훅 발송 성공: paymentId={}", traceId, paymentId);
        } catch (Exception e) {
            log.error("[{}] 웹훅 발송 실패, DLQ에 저장: paymentId={}, error={}", traceId, paymentId, e.getMessage());
            // DLQ에 저장 → 스케줄러가 재시도
            webhookRetryRepository.save(paymentId, merchantId, traceId);
        }
    }
}
