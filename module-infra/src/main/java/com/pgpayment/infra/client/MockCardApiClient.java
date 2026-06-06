package com.pgpayment.infra.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 개발 환경용 카드사 Mock — @Profile 학습 포인트 (모듈 15)
 *
 * @Profile("dev"): dev 환경에서만 이 Bean이 활성화
 * @Profile("!dev"): dev 제외 환경에서 CardApiClient 활성화
 *
 * 장점:
 *   - 실제 카드사 연동 없이 개발/테스트 가능
 *   - Resilience4j CB 동작 테스트: forceFailure=true로 실패 시뮬레이션
 *   - CI 환경에서 외부 의존성 없이 빌드 가능
 *
 * 단점:
 *   - Mock과 실제 동작이 달라질 수 있음 → 통합 테스트 필수
 *   - @Profile 관리가 복잡해지면 환경별 설정 추적이 어려움
 */
@Slf4j
@Component
@Profile("dev")
public class MockCardApiClient {

    private static boolean forceFailure = false;

    public static void setForceFailure(boolean fail) {
        forceFailure = fail;
    }

    public Map<String, Object> approve(String cardCompany, String cardNumber, Long amount) {
        if (forceFailure) {
            log.warn("[MOCK] 카드사 강제 실패 시뮬레이션: cardCompany={}", cardCompany);
            throw new RuntimeException("카드사 Mock 강제 실패");
        }

        log.info("[MOCK] 카드사 승인 성공: cardCompany={}, amount={}", cardCompany, amount);
        return Map.of(
                "approvalNumber", "MOCK-" + System.currentTimeMillis(),
                "cardCompany", cardCompany,
                "amount", amount,
                "status", "APPROVED"
        );
    }
}
