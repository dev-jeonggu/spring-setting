package com.pgpayment.api.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 결제 성공률 헬스체크 — Custom HealthIndicator 학습 포인트 (모듈 7)
 *
 * Spring Actuator /actuator/health에 커스텀 지표 추가
 *
 * 기본 제공 HealthIndicator: DB, Redis, Disk, Mail 등
 * 커스텀 HealthIndicator: 비즈니스 지표 (결제 성공률, 카드사 상태 등)
 *
 * 장점:
 *   - 인프라 레벨이 아닌 비즈니스 레벨 헬스체크
 *   - k8s Liveness/Readiness Probe와 연동 가능
 *   - 운영팀 대시보드에서 즉시 확인
 *
 * 단점:
 *   - 헬스체크 자체가 부하를 줄 수 있음 (DB 쿼리 주의)
 *   - 임계값 설정이 주관적 → 잘못 설정 시 false alarm
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSuccessRateHealthIndicator implements HealthIndicator {

    private static final double MIN_SUCCESS_RATE = 0.95; // 95% 미만이면 UNHEALTHY
    private final StringRedisTemplate redisTemplate;

    @Override
    public Health health() {
        try {
            double successRate = calculateSuccessRate();

            if (successRate < MIN_SUCCESS_RATE) {
                return Health.down()
                        .withDetail("successRate", String.format("%.2f%%", successRate * 100))
                        .withDetail("threshold", String.format("%.0f%%", MIN_SUCCESS_RATE * 100))
                        .withDetail("message", "결제 성공률이 임계값 미만입니다")
                        .build();
            }

            return Health.up()
                    .withDetail("successRate", String.format("%.2f%%", successRate * 100))
                    .withDetail("threshold", String.format("%.0f%%", MIN_SUCCESS_RATE * 100))
                    .build();

        } catch (Exception e) {
            log.error("헬스체크 실패", e);
            return Health.unknown()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private double calculateSuccessRate() {
        try {
            String successStr = redisTemplate.opsForValue().get("metrics:payment:success:1h");
            String totalStr = redisTemplate.opsForValue().get("metrics:payment:total:1h");

            if (totalStr == null || "0".equals(totalStr)) return 1.0;

            long success = successStr != null ? Long.parseLong(successStr) : 0;
            long total = Long.parseLong(totalStr);
            return (double) success / total;
        } catch (Exception e) {
            log.debug("Redis 미연결 — 기본값 반환: {}", e.getMessage());
            return 1.0; // Redis 없으면 정상으로 간주
        }
    }
}
