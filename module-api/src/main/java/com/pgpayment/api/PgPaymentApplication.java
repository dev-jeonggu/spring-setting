package com.pgpayment.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * PG 결제 서비스 메인 애플리케이션
 *
 * @SpringBootApplication = @Configuration + @EnableAutoConfiguration + @ComponentScan
 *
 * 멀티모듈 구성 시 주의:
 *   @EntityScan: 다른 모듈의 @Entity 클래스를 스캔하도록 명시
 *   @EnableJpaRepositories: 다른 모듈의 Repository 스캔
 *   @ComponentScan: 기본값은 현재 패키지 하위 → 다른 모듈 패키지 명시 필요
 *
 * @EnableJpaAuditing: @CreatedDate, @LastModifiedDate 자동 관리
 */
@SpringBootApplication(scanBasePackages = "com.pgpayment")
@EntityScan(basePackages = {"com.pgpayment.core.domain", "com.pgpayment.support.audit"})
@EnableJpaRepositories(basePackages = {
        "com.pgpayment.core.repository",
        "com.pgpayment.support.audit"
})
@EnableJpaAuditing
public class PgPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PgPaymentApplication.class, args);
    }
}
