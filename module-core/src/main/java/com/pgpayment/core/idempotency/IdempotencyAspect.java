package com.pgpayment.core.idempotency;

import com.pgpayment.support.exception.PgErrorCode;
import com.pgpayment.support.exception.PgException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 멱등성 AOP — Redisson 분산 락 + Redis setIfAbsent 이중 보호
 *
 * [개선 배경]
 * setIfAbsent 단독 사용 시 두 가지 경쟁 조건이 존재한다.
 *
 * 케이스 1: Redis 장애 → 폴백으로 모든 요청 통과
 *   Redis 예외 발생 시 catch 블록에서 그냥 proceed() → 동시 요청 모두 결제 실행
 *   재현: IdempotencyRaceConditionTest#case1_redisDown_bothRequestsPass
 *
 * 케이스 2: 결제 실패 후 키 삭제 타이밍 경쟁
 *   실패 시 키를 delete()하는 순간, 대기 중이던 재요청이 새 요청처럼 진입
 *   재현: IdempotencyRaceConditionTest#case2_keyDeletedAfterFailure_retrySlipsThrough
 *
 * [해결 방법]
 * Redisson 분산 락으로 "키 확인 → 결제 실행" 전체 구간을 임계 영역으로 보호
 *   - 동일 idempotencyKey에 대한 요청을 한 번에 하나만 진입 허용
 *   - Redis 장애 시 락 획득 자체가 실패 → 폴백으로 통과되지 않음
 *   - 결제 실패 후 키 삭제 시점에도 락이 다음 요청을 순서대로 처리
 *
 * 장점:
 *   - 경쟁 조건 완전 차단
 *   - tryLock 타임아웃으로 무한 대기 방지
 *
 * 단점:
 *   - Redis 완전 장애 시 결제 불가 → Redis HA(Sentinel/Cluster) 필수
 *   - 락 획득 대기 시간(waitTime)만큼 응답 지연 가능
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private static final String KEY_PREFIX  = "idempotency:";
    private static final String LOCK_PREFIX = "lock:idempotency:";
    private static final long   WAIT_SEC    = 3L;
    private static final long   LEASE_SEC   = 30L;

    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;

    @Around("@annotation(idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String idempotencyKey = extractKey(pjp, idempotent.keyParam());

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return pjp.proceed();
        }

        String redisKey = KEY_PREFIX  + idempotencyKey;
        String lockKey  = LOCK_PREFIX + idempotencyKey;

        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = lock.tryLock(WAIT_SEC, LEASE_SEC, TimeUnit.SECONDS);
        if (!locked) {
            log.warn("분산 락 획득 실패 (처리 중인 요청): idempotencyKey={}", idempotencyKey);
            throw new PgException(PgErrorCode.DUPLICATE_PAYMENT,
                    "현재 처리 중인 요청입니다. 잠시 후 재시도해 주세요. idempotencyKey=" + idempotencyKey);
        }

        try {
            // 락 내부에서 멱등성 키 확인 → 경쟁 조건 차단
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "PROCESSING", Duration.ofSeconds(idempotent.ttlSeconds()));

            if (Boolean.FALSE.equals(isNew)) {
                log.warn("중복 결제 요청 감지: idempotencyKey={}", idempotencyKey);
                throw new PgException(PgErrorCode.DUPLICATE_PAYMENT,
                        "이미 처리됐거나 처리 중인 결제입니다. idempotencyKey=" + idempotencyKey);
            }

            try {
                Object result = pjp.proceed();
                redisTemplate.opsForValue().set(redisKey, "COMPLETED",
                        Duration.ofSeconds(idempotent.ttlSeconds()));
                return result;
            } catch (Exception e) {
                redisTemplate.delete(redisKey);
                throw e;
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String extractKey(ProceedingJoinPoint pjp, String paramName) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                return args[i] != null ? args[i].toString() : null;
            }
        }
        return null;
    }
}
