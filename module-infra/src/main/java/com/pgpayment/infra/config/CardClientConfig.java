package com.pgpayment.infra.config;

import com.pgpayment.infra.properties.CardProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient + Resilience4j 설정 — 모듈 10, 17 학습 포인트
 *
 * WebClient vs RestTemplate:
 *   RestTemplate: 동기 블로킹 (Spring 5.0부터 maintenance mode)
 *   WebClient: 논블로킹 비동기, reactive stream 지원
 *
 * 장점(WebClient):
 *   - 논블로킹 → 카드사 응답 대기 중 다른 요청 처리 가능
 *   - Resilience4j reactive 연동 지원
 *   - ConnectionTimeout, ReadTimeout 개별 설정 가능
 *
 * 단점(WebClient):
 *   - 학습 곡선 높음 (Mono/Flux, reactive 패러다임)
 *   - 동기 코드베이스에 섞으면 block() 호출 → 장점 소멸
 *   - 디버깅이 RestTemplate보다 어려움
 */
@Configuration
@RequiredArgsConstructor
public class CardClientConfig {

    private final CardProperties cardProperties;

    @Bean("shinhanCardClient")
    public WebClient shinhanCardClient() {
        return buildWebClient(cardProperties.getShinhanUrl(), cardProperties.getTimeout());
    }

    @Bean("kbCardClient")
    public WebClient kbCardClient() {
        return buildWebClient(cardProperties.getKbUrl(), cardProperties.getTimeout());
    }

    @Bean("samsungCardClient")
    public WebClient samsungCardClient() {
        return buildWebClient(cardProperties.getSamsungUrl(), cardProperties.getTimeout());
    }

    private WebClient buildWebClient(String baseUrl, int timeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .failureRateThreshold(50)     // 실패율 50% 초과 시 OPEN
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

        return CircuitBreakerRegistry.of(config);
    }
}
