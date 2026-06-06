package com.pgpayment.infra.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * @ConfigurationProperties 학습 포인트 — 모듈 14
 *
 * @Value vs @ConfigurationProperties 비교:
 *
 *   @Value("${pg.card.shinhan-url}")
 *     - 단순, 하나씩
 *     - 타입 자동 변환 제한적
 *     - IDE 자동완성 없음 (추가 설정 필요)
 *     - 관련 설정이 흩어짐
 *
 *   @ConfigurationProperties(prefix = "pg.card")
 *     - 관련 설정 객체로 묶음
 *     - 타입 안전 (@Validated로 검증 가능)
 *     - IDE 자동완성 지원 (spring-boot-configuration-processor)
 *     - 계층 구조 설정 지원
 *
 * 장점: 환경별(dev/staging/prod) 설정 분리, 타입 안전성, 검증 가능
 * 단점: 클래스 추가 필요, 단순 설정엔 오버엔지니어링
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "pg.card")
public class CardProperties {

    @NotBlank
    private String shinhanUrl;

    @NotBlank
    private String kbUrl;

    @NotBlank
    private String samsungUrl;

    @Min(1000)
    @Max(30000)
    private int timeout = 5000;
}
