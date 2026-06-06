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
        String value = paymentId + "|" + merchantId + "|" + traceId;
        redisTemplate.opsForSet().add(DLQ_KEY, value);
        log.debug("웹훅 DLQ 저장: {}", value);
    }

    public Set<String> findAll() {
        return redisTemplate.opsForSet().members(DLQ_KEY);
    }

    public void remove(String value) {
        redisTemplate.opsForSet().remove(DLQ_KEY, value);
    }
}
