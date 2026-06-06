package com.pgpayment.support.aop;

import com.pgpayment.support.mdcfilter.TraceIdHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * LoggingAspect — AOP 횡단 관심사 분리 학습 포인트
 *
 * @Around: 메서드 실행 전/후 모두 제어 가능 (가장 강력)
 * @Before: 메서드 실행 전만 (결과 조작 불가)
 * @AfterReturning: 정상 반환 후만
 * @AfterThrowing: 예외 발생 후만
 *
 * 장점:
 *   - 서비스 코드에 로깅 코드 없음 → 단일 책임 원칙
 *   - 전체 API 실행시간 일괄 측정
 *
 * 단점:
 *   - 프록시 기반이라 같은 클래스 내 자기 호출(self-invocation)엔 동작 안 함
 *   - @Async 메서드는 별도 프록시라 MDC traceId 전파 이슈 발생
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    private static final long SLOW_THRESHOLD_MS = 100;

    @Around("execution(* com.pgpayment..service..*(..))")
    public Object logServiceExecution(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        long start = System.currentTimeMillis();

        try {
            Object result = pjp.proceed();
            long elapsed = System.currentTimeMillis() - start;

            if (elapsed > SLOW_THRESHOLD_MS) {
                log.warn("[{}] SLOW SERVICE {} - {}ms (임계값: {}ms)",
                        TraceIdHolder.get(), method, elapsed, SLOW_THRESHOLD_MS);
            } else {
                log.debug("[{}] {} - {}ms", TraceIdHolder.get(), method, elapsed);
            }

            return result;
        } catch (Exception e) {
            log.error("[{}] {} 실행 실패: {}", TraceIdHolder.get(), method, e.getMessage());
            throw e;
        }
    }
}
