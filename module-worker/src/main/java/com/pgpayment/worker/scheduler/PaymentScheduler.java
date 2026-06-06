package com.pgpayment.worker.scheduler;

import com.pgpayment.core.domain.Payment;
import com.pgpayment.core.domain.PaymentStatus;
import com.pgpayment.core.repository.PaymentRepository;
import com.pgpayment.worker.webhook.WebhookRetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 결제 배치 스케줄러 — @Scheduled 학습 포인트 (모듈 12)
 *
 * @Scheduled 파라미터:
 *   fixedRate: 이전 실행 시작 시점 기준 주기 (병렬 가능)
 *   fixedDelay: 이전 실행 완료 시점 기준 주기 (순차)
 *   cron: cron 표현식 ("초 분 시 일 월 요일")
 *
 * 장점:
 *   - 별도 스케줄링 서버 없이 애플리케이션 내에서 배치 처리
 *   - @EnableScheduling + ThreadPoolTaskScheduler로 병렬 실행
 *
 * 단점:
 *   - 다중 인스턴스 배포 시 모든 인스턴스에서 실행 → 중복 실행 문제
 *     → 해결: ShedLock(@SchedulerLock), DB 분산 락, Quartz 클러스터
 *   - 단일 쓰레드 기본값 → AsyncConfig에서 ThreadPoolTaskScheduler 설정 필요
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentScheduler {

    private final PaymentRepository paymentRepository;
    private final WebhookRetryRepository webhookRetryRepository;

    // 매시간 — PENDING 30분 초과 건 강제 FAILED
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expirePendingPayments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<Payment> stuckPayments = paymentRepository
                .findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, threshold);

        log.info("[Scheduler] PENDING 만료 처리: {}건", stuckPayments.size());
        stuckPayments.forEach(p -> p.fail("결제 대기 시간 초과"));
    }

    // 매분 — 웹훅 발송 실패 건 DLQ 재시도
    @Scheduled(fixedDelay = 60000)
    public void retryFailedWebhooks() {
        Set<String> dlqItems = webhookRetryRepository.findAll();
        if (dlqItems == null || dlqItems.isEmpty()) return;

        log.info("[Scheduler] 웹훅 DLQ 재시도: {}건", dlqItems.size());
        dlqItems.forEach(item -> {
            String[] parts = item.split("\\|");
            if (parts.length == 3) {
                String paymentId = parts[0];
                log.info("[Scheduler] 웹훅 재시도: paymentId={}", paymentId);
                webhookRetryRepository.remove(item);
                // 실제 구현에서는 WebhookService.send() 재호출
            }
        });
    }

    // 매일 00:00 — 전일 결제 정산 집계
    @Scheduled(cron = "0 0 0 * * *")
    public void settleYesterdayPayments() {
        log.info("[Scheduler] 정산 배치 시작: {}", LocalDateTime.now());
        // 정산 로직 (SettlementService.settle() 호출)
    }
}
