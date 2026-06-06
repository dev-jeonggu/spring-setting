package com.pgpayment.core.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 가맹점 엔티티
 *
 * Serializable 구현: Redis 캐시에 직렬화/역직렬화되므로 필수
 * @Cacheable 학습 포인트 참고
 */
@Entity
@Table(name = "merchant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Merchant implements Serializable {

    @Id
    private String merchantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String apiKey;

    @Column(nullable = false)
    private String webhookUrl;

    @Column(nullable = false)
    private Double feeRate;

    @Column(nullable = false)
    private Boolean active;

    @Builder
    public Merchant(String merchantId, String name, String apiKey,
                    String webhookUrl, Double feeRate) {
        this.merchantId = merchantId;
        this.name = name;
        this.apiKey = apiKey;
        this.webhookUrl = webhookUrl;
        this.feeRate = feeRate;
        this.active = true;
    }
}
