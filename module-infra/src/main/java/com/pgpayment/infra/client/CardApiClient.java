package com.pgpayment.infra.client;

import com.pgpayment.support.exception.PgErrorCode;
import com.pgpayment.support.exception.PgException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 카드사 API 클라이언트 — Resilience4j Circuit Breaker 학습 포인트 (모듈 10)
 *
 * Circuit Breaker 3가지 상태:
 *   CLOSED: 정상 상태, 모든 요청 통과
 *   OPEN:   장애 상태, 모든 요청 즉시 차단 (카드사 회복 시간 제공)
 *   HALF_OPEN: 테스트 상태, 일부 요청 통과하여 회복 여부 확인
 *
 * 장점:
 *   - 신한카드 장애 시 해당 CB만 OPEN → 다른 카드사 영향 없음
 *   - 장애 카드사에 계속 요청하지 않음 → 쓰레드 해방, 서버 안정
 *   - 자동 회복 (HALF_OPEN → CLOSED)
 *
 * 단점:
 *   - 설정값 튜닝 필요 (슬라이딩 윈도우, 실패율 임계값)
 *   - OPEN 상태에서 즉시 실패 → 가맹점에게 카드사 장애 안내 필요
 *   - 짧은 순간 실패율 급등 시 오동작 가능
 *
 * 면접 포인트:
 *   Q. 신한카드가 느린데 서버 전체가 왜 죽었나?
 *   A. 쓰레드가 응답 대기 상태로 블로킹 → 풀 고갈 → 다른 요청도 처리 불가
 *      CB + Bulkhead로 격리하면 신한카드 전용 쓰레드만 고갈되고 나머지는 정상
 */
@Slf4j
@Component
@Profile("!dev") // dev 환경은 MockCardApiClient 사용
public class CardApiClient {

    private final WebClient shinhanClient;
    private final WebClient kbClient;
    private final WebClient samsungClient;
    private final CircuitBreaker shinhanCB;
    private final CircuitBreaker kbCB;
    private final CircuitBreaker samsungCB;

    public CardApiClient(
            @Qualifier("shinhanCardClient") WebClient shinhanClient,
            @Qualifier("kbCardClient") WebClient kbClient,
            @Qualifier("samsungCardClient") WebClient samsungClient,
            CircuitBreakerRegistry registry) {
        this.shinhanClient = shinhanClient;
        this.kbClient = kbClient;
        this.samsungClient = samsungClient;
        // 카드사별 독립 CB — 한 카드사 장애가 다른 카드사에 영향 없음
        this.shinhanCB = registry.circuitBreaker("shinhan-card");
        this.kbCB = registry.circuitBreaker("kb-card");
        this.samsungCB = registry.circuitBreaker("samsung-card");
    }

    public Map<String, Object> approve(String cardCompany, String cardNumber, Long amount) {
        return switch (cardCompany.toLowerCase()) {
            case "shinhan" -> callWithCB(shinhanClient, shinhanCB, cardNumber, amount);
            case "kb"      -> callWithCB(kbClient, kbCB, cardNumber, amount);
            case "samsung" -> callWithCB(samsungClient, samsungCB, cardNumber, amount);
            default -> throw new PgException(PgErrorCode.CARD_COMPANY_ERROR, "지원하지 않는 카드사: " + cardCompany);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callWithCB(WebClient client, CircuitBreaker cb,
                                            String cardNumber, Long amount) {
        return client.post()
                .uri("/approve")
                .bodyValue(Map.of("cardNumber", cardNumber, "amount", amount))
                .retrieve()
                .bodyToMono(Map.class)
                .transform(CircuitBreakerOperator.of(cb))
                .onErrorMap(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
                        e -> new PgException(PgErrorCode.CIRCUIT_BREAKER_OPEN, "카드사 서킷브레이커 개방 상태"))
                .onErrorMap(Exception.class,
                        e -> new PgException(PgErrorCode.CARD_COMPANY_ERROR, e.getMessage()))
                .block(); // 동기 코드베이스와 연동
    }
}
