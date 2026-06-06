package com.pgpayment.core.idempotency;

import java.lang.annotation.*;

/**
 * 멱등성 보장 어노테이션
 *
 * 사용법: @Idempotent 붙은 메서드 → IdempotencyAspect가 Redis로 중복 검사
 *
 * keyParam: 멱등성 키로 사용할 파라미터 이름 (기본값: X-Idempotency-Key 헤더)
 * ttlSeconds: Redis TTL (기본 24시간)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
    String keyParam() default "idempotencyKey";
    long ttlSeconds() default 86400;
}
