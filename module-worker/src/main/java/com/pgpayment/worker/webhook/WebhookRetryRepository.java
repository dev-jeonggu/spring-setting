package com.pgpayment.worker.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Set;

/**
 * 웹훅 재시도 DLQ (Dead Letter Queue) — Redis 기반 구현
 *
 * 간단한 구현: Redis Set에 실패한 paymentId 저장
 * 실무: RabbitMQ DLX, Kafka DLT, DB 테이블 등 사용
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class WebhookRetryRepository {

    private static final String DLQ_KEY = "webhook:dlq";
    private final StringRedisTemplate redisTemplate;

    public void save(String paymentId, String merchantId, String traceId) {
        try {
            String value = paymentId + "|" + merchantId + "|" + traceId;
            redisTemplate.opsForSet().add(DLQ_KEY, value);
            log.debug("웹훅 DLQ 저장: {}", value);
        } catch (Exception e) {
            log.warn("Redis 미연결 — DLQ 저장 스킵: {}", e.getMessage());
        }
    }

    public Set<String> findAll() {
        try {
            return redisTemplate.opsForSet().members(DLQ_KEY);
        } catch (Exception e) {
            log.debug("Redis 미연결 — DLQ 조회 스킵: {}", e.getMessage());
            return java.util.Set.of();
        }
    }

    public void remove(String value) {
        try {
            redisTemplate.opsForSet().remove(DLQ_KEY, value);
        } catch (Exception e) {
            log.warn("Redis 미연결 — DLQ 삭제 스킵: {}", e.getMessage());
        }
    }
}
