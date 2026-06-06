package com.pgpayment.core.service;

import com.pgpayment.core.domain.Merchant;
import com.pgpayment.core.repository.MerchantRepository;
import com.pgpayment.support.exception.PgErrorCode;
import com.pgpayment.support.exception.PgException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가맹점 캐시 서비스 — @Cacheable + Redis 학습 포인트
 *
 * @Cacheable(value = "merchant", key = "#merchantId")
 *   → 첫 조회: DB 조회 후 Redis에 저장
 *   → 이후 조회: Redis에서 바로 반환 (DB 미접근)
 *
 * 장점:
 *   - 가맹점 정보는 자주 바뀌지 않음 → 캐시 적중률 높음
 *   - 결제 요청마다 DB 조회 → 캐시 조회로 대체 → DB 부하 대폭 감소
 *
 * 단점:
 *   - 가맹점 정보 변경 시 캐시 무효화(@CacheEvict) 필수
 *   - Redis 장애 시 DB 직접 조회로 폴백 필요 (현재 미구현)
 *   - 캐시 키 설계 실수 시 잘못된 데이터 반환 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantCacheService {

    private final MerchantRepository merchantRepository;

    @Cacheable(value = "merchant", key = "#merchantId")
    @Transactional(readOnly = true)
    public Merchant findById(String merchantId) {
        log.debug("캐시 미스 — DB 조회: merchantId={}", merchantId);
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new PgException(PgErrorCode.MISSING_MERCHANT, merchantId));
    }

    @Cacheable(value = "merchant-by-apikey", key = "#apiKey")
    @Transactional(readOnly = true)
    public Merchant findByApiKey(String apiKey) {
        log.debug("캐시 미스 — API Key로 DB 조회");
        return merchantRepository.findByApiKey(apiKey)
                .orElseThrow(() -> new PgException(PgErrorCode.INVALID_API_KEY, "API Key 불일치"));
    }

    @CacheEvict(value = {"merchant", "merchant-by-apikey"}, allEntries = true)
    public void evictAll() {
        log.info("가맹점 캐시 전체 무효화");
    }
}
