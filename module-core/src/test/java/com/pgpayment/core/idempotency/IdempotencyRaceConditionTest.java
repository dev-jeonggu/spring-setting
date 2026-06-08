package com.pgpayment.core.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotencyAspect의 두 가지 경쟁 조건 재현 테스트
 *
 * 케이스 1: Redis 장애 시 폴백 → 모든 요청 통과
 * 케이스 2: 결제 실패 후 키 삭제 타이밍 → 동시 재요청 중복 실행
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyRaceConditionTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    // AOP 없이 로직만 직접 검증하는 슬림한 래퍼
    private SlimIdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        guard = new SlimIdempotencyGuard(redisTemplate);
    }

    // ──────────────────────────────────────────────────────────
    // 케이스 1: Redis 장애 → 폴백으로 모든 요청 통과
    // ──────────────────────────────────────────────────────────
    @Test
    @DisplayName("[케이스 1] Redis 장애 시 동시 요청 2개가 모두 통과된다")
    void case1_redisDown_bothRequestsPass() throws InterruptedException {
        // given: Redis가 예외를 던지는 상황
        when(valueOps.setIfAbsent(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Runnable request = () -> {
            ready.countDown();
            try {
                start.await();
                boolean passed = guard.tryAcquire("ORDER-001");
                if (passed) executionCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(request);
        executor.submit(request);
        ready.await();
        start.countDown();
        executor.shutdown();
        executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS);

        // then: 두 요청 모두 통과 → 중복 결제 발생
        assertThat(executionCount.get())
                .as("Redis 장애 시 폴백으로 두 요청이 모두 통과됨 → 중복 결제 위험")
                .isEqualTo(2);
    }

    // ──────────────────────────────────────────────────────────
    // 케이스 2: 결제 실패 후 키 삭제 타이밍에 재요청 진입
    // ──────────────────────────────────────────────────────────
    @Test
    @DisplayName("[케이스 2] 결제 실패로 키 삭제 직후 재요청이 들어오면 중복 실행된다")
    void case2_keyDeletedAfterFailure_retrySlipsThrough() {
        // given: 첫 요청 → 키 없음 → 통과 → 결제 실패 → 키 삭제
        when(valueOps.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true)   // 첫 번째 요청: 키 없음 → 통과
                .thenReturn(true);  // 재요청: 키가 삭제됐으니 또 통과

        AtomicInteger executionCount = new AtomicInteger(0);

        // 첫 번째 요청 실행 (결제 실패 시뮬레이션 → 키 삭제)
        boolean firstPass = guard.tryAcquire("ORDER-001");
        if (firstPass) {
            executionCount.incrementAndGet();
            guard.releaseOnFailure("ORDER-001"); // 실패 → delete(key)
        }

        // 재요청 (키가 없으니 또 통과)
        boolean retryPass = guard.tryAcquire("ORDER-001");
        if (retryPass) executionCount.incrementAndGet();

        // then: 두 번 실행 → 중복 결제
        assertThat(executionCount.get())
                .as("결제 실패 후 키 삭제 → 재요청이 새 요청처럼 통과됨")
                .isEqualTo(2);
    }

    // ──────────────────────────────────────────────────────────
    // 기대 동작: 정상 케이스 (중복 차단)
    // ──────────────────────────────────────────────────────────
    @Test
    @DisplayName("[정상] Redis 정상 시 두 번째 요청은 차단된다")
    void normalCase_secondRequestBlocked() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true)   // 첫 번째: 통과
                .thenReturn(false); // 두 번째: 차단

        AtomicInteger executionCount = new AtomicInteger(0);

        if (guard.tryAcquire("ORDER-001")) executionCount.incrementAndGet();
        if (guard.tryAcquire("ORDER-001")) executionCount.incrementAndGet();

        assertThat(executionCount.get()).isEqualTo(1);
    }

    /**
     * IdempotencyAspect의 핵심 로직만 추출한 테스트용 래퍼.
     * AOP 프록시 없이 경쟁 조건 로직만 단독 검증.
     */
    static class SlimIdempotencyGuard {
        private static final String PREFIX = "idempotency:";
        private final StringRedisTemplate redis;

        SlimIdempotencyGuard(StringRedisTemplate redis) {
            this.redis = redis;
        }

        boolean tryAcquire(String key) {
            try {
                Boolean isNew = redis.opsForValue()
                        .setIfAbsent(PREFIX + key, "PROCESSING",
                                java.time.Duration.ofSeconds(30));
                return Boolean.TRUE.equals(isNew);
            } catch (Exception e) {
                // ← 현재 IdempotencyAspect의 폴백: 예외 시 그냥 통과
                return true;
            }
        }

        void releaseOnFailure(String key) {
            // 현재 IdempotencyAspect 75번째 줄: 실패 시 키 삭제
            redis.delete(PREFIX + key);
        }
    }
}
