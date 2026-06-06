package com.pgpayment.core.idempotency;

import com.pgpayment.support.exception.PgErrorCode;
import com.pgpayment.support.exception.PgException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;
import java.time.Duration;

/**
 * 멱등성 AOP 구현 — Redis 기반 중복 결제 차단
 *
 * 흐름:
 *   1. @Idempotent 메서드 진입 시 idempotencyKey 파라미터 추출
 *   2. Redis에 "idempotency:{key}" 존재 여부 확인
 *   3. 존재하면 → DUPLICATE_PAYMENT 예외 (실제로는 기존 결과 반환이 더 올바름)
 *   4. 없으면 → 메서드 실행 → Redis에 TTL과 함께 저장
 *
 * 장점:
 *   - 네트워크 타임아웃 후 재요청 시 중복 승인 완전 차단
 *   - AOP로 서비스 코드 무오염
 *
 * 단점:
 *   - Redis 장애 시 멱등성 검증 불가 → 폴백 전략 필요
 *   - 분산 환경에서 Redis 클러스터 구성 필요
 *   - 현재 구현은 응답 자체를 캐싱하지 않음 (개선 여지)
 *
 * 면접 포인트:
 *   Q. Redis가 다운되면 어떻게 되나?
 *   A. try-catch로 Redis 실패 시 처리 진행하되 별도 경고 로그,
 *      또는 DB 테이블을 fallback으로 사용
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        String idempotencyKey = extractKey(pjp, idempotent.keyParam());

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return pjp.proceed();
        }

        String redisKey = KEY_PREFIX + idempotencyKey;

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSING", Duration.ofSeconds(idempotent.ttlSeconds()));

        if (Boolean.FALSE.equals(isNew)) {
            log.warn("중복 결제 요청 감지: idempotencyKey={}", idempotencyKey);
            throw new PgException(PgErrorCode.DUPLICATE_PAYMENT,
                    "이미 처리 중이거나 완료된 결제입니다. idempotencyKey=" + idempotencyKey);
        }

        try {
            Object result = pjp.proceed();
            redisTemplate.opsForValue().set(redisKey, "COMPLETED", Duration.ofSeconds(idempotent.ttlSeconds()));
            return result;
        } catch (Exception e) {
            redisTemplate.delete(redisKey); // 실패 시 키 삭제 → 재시도 허용
            throw e;
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
