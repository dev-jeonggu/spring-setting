package com.pgpayment.core.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 분산 락 적용 후 경쟁 조건 차단 검증
 *
 * IdempotencyRaceConditionTest(개선 전)와 쌍을 이루는 테스트.
 * 동일한 시나리오에서 분산 락이 중복 실행을 막는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class DistributedLockIdempotencyTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock RedissonClient redissonClient;
    @Mock RLock rLock;

    private SlimDistributedLockGuard guard;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        guard = new SlimDistributedLockGuard(redisTemplate, redissonClient);
    }

    // ──────────────────────────────────────────────────────────
    // 케이스 1 수정: Redis 장애 → 락 획득 실패 → 통과 안 됨
    // ──────────────────────────────────────────────────────────
    @Test
    @DisplayName("[케이스 1 수정] Redis 장애 시 락 획득 자체가 실패 → 요청 차단")
    void case1_fixed_redisDown_lockFailsAndBlocks() throws Exception {
        // given: 락 획득 실패 (Redis 장애 상황)
        when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(false);

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
        executor.awaitTermination(3, TimeUnit.SECONDS);

        // then: 락을 못 얻으면 아무도 통과 못 함
        assertThat(executionCount.get())
                .as("분산 락 획득 실패 시 폴백 없이 차단")
                .isEqualTo(0);
    }

    // ──────────────────────────────────────────────────────────
    // 케이스 2 수정: 락 내부에서 순차 처리 → 재요청이 COMPLETED 확인 후 차단
    // ──────────────────────────────────────────────────────────
    @Test
    @DisplayName("[케이스 2 수정] 첫 요청 성공 후 재요청은 락 내에서 COMPLETED 확인 후 차단")
    void case2_fixed_retryBlockedInsideLock() throws Exception {
        // given: 락은 항상 획득 성공
        when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        // 첫 요청: 키 없음(true) → 실행 → COMPLETED 저장
        // 재요청: 키 있음(false) → 차단
        when(valueOps.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true)   // 첫 번째: 통과
                .thenReturn(false); // 재요청: 차단

        AtomicInteger executionCount = new AtomicInteger(0);

        // 첫 번째 요청 실행
        boolean firstPass = guard.tryAcquire("ORDER-001");
        if (firstPass) executionCount.incrementAndGet();

        // 재요청 (락 내부에서 setIfAbsent가 false → 차단)
        boolean retryPass = guard.tryAcquire("ORDER-001");
        if (retryPass) executionCount.incrementAndGet();

        // then: 첫 번째만 통과
        assertThat(executionCount.get())
                .as("재요청은 락 내부에서 COMPLETED 키를 보고 차단됨")
                .isEqualTo(1);
    }

    // ──────────────────────────────────────────────────────────
    // 정상: 락 획득 성공 + 최초 요청만 통과
    // ──────────────────────────────────────────────────────────
    @Test
    @DisplayName("[정상] 락 획득 후 최초 요청만 통과, 중복은 차단")
    void normalCase_onlyFirstRequestPasses() throws Exception {
        when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(valueOps.setIfAbsent(anyString(), anyString(), any()))
                .thenReturn(true)
                .thenReturn(false);

        AtomicInteger count = new AtomicInteger(0);
        if (guard.tryAcquire("ORDER-001")) count.incrementAndGet();
        if (guard.tryAcquire("ORDER-001")) count.incrementAndGet();

        assertThat(count.get()).isEqualTo(1);
    }

    /**
     * 분산 락 적용 후 IdempotencyAspect 핵심 로직 래퍼.
     * RLock.tryLock 실패 시 즉시 차단 — 폴백 없음.
     */
    static class SlimDistributedLockGuard {
        private static final String KEY_PREFIX  = "idempotency:";
        private static final String LOCK_PREFIX = "lock:idempotency:";

        private final StringRedisTemplate redis;
        private final RedissonClient redissonClient;

        SlimDistributedLockGuard(StringRedisTemplate redis, RedissonClient redissonClient) {
            this.redis = redis;
            this.redissonClient = redissonClient;
        }

        boolean tryAcquire(String key) {
            RLock lock = redissonClient.getLock(LOCK_PREFIX + key);
            try {
                boolean locked = lock.tryLock(3, 30, TimeUnit.SECONDS);
                if (!locked) return false; // 락 획득 실패 → 폴백 없이 차단

                try {
                    Boolean isNew = redis.opsForValue()
                            .setIfAbsent(KEY_PREFIX + key, "PROCESSING",
                                    java.time.Duration.ofSeconds(30));
                    return Boolean.TRUE.equals(isNew);
                } finally {
                    if (lock.isHeldByCurrentThread()) lock.unlock();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
