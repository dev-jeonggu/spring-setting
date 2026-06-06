package com.pgpayment.infra.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis CacheManager Bean 직접 등록 — 모듈 5 학습 포인트
 *
 * Spring Boot 자동 설정(@EnableCaching만 있으면 ConcurrentMapCacheManager가 기본)
 * → 실무에서는 Redis CacheManager를 직접 Bean으로 등록해야 함
 *
 * 장점:
 *   - 캐시별 TTL 개별 설정 가능 (가맹점 캐시 1시간, 환율 캐시 5분 등)
 *   - JSON 직렬화로 타입 정보 포함 → 역직렬화 안전
 *   - 분산 서버 환경에서 캐시 공유
 *
 * 단점:
 *   - 직렬화 설정 복잡 (Jackson 설정 오류 시 ClassCastException)
 *   - Redis 장애 시 캐시 전체 불가 → CachingConfigurerSupport로 오류 처리 필요
 *   - 캐시 키 충돌 가능 → 네이밍 전략 명시 필요
 *
 * 면접 포인트:
 *   Q. @Cacheable만 붙이면 되는 것 아닌가?
 *   A. @EnableCaching 없으면 동작 안 함. CacheManager Bean 없으면 In-Memory 캐시 사용.
 *      실무에서는 TTL, 직렬화 전략, Redis 연결을 명시적으로 설정해야 함.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfBaseType(Object.class)
                                .build(),
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer))
                .disableCachingNullValues();

        // 캐시별 TTL 개별 설정
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("merchant", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("merchant-by-apikey", defaultConfig.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
