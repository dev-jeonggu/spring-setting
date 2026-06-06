package com.pgpayment.infra.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * HikariCP DataSource 설정 — 모듈 1 학습 포인트
 *
 * 핵심 파라미터 설명:
 *   maximumPoolSize: 최대 커넥션 수
 *     → 공식: (CPU 코어 수 * 2) + 유효 스핀들 수
 *     → 너무 크면: 컨텍스트 스위칭 오버헤드, DB 서버 부하 증가
 *     → 너무 작으면: 피크 시간대 커넥션 대기 → 레이턴시 급증
 *
 *   minimumIdle: 최소 유지 커넥션
 *     → 0이면 유휴 시 모두 반납 → 트래픽 급증 시 커넥션 생성 지연
 *     → 권장: maximumPoolSize와 동일 (고정 풀)
 *
 *   connectionTimeout: 커넥션 획득 대기 시간
 *     → 30초 초과 시 SQLException → 너무 짧으면 일시적 부하에도 오류
 *
 *   idleTimeout: 유휴 커넥션 제거 시간 (maximumPoolSize > minimumIdle 일 때만 동작)
 *
 *   maxLifetime: 커넥션 최대 생존 시간
 *     → DB wait_timeout보다 짧게 설정 필수 (DB가 먼저 끊으면 JDBC 오류)
 *
 * 장점: 빠른 커넥션 획득, 풀 재사용으로 DB 연결 오버헤드 제거
 * 단점: 풀 크기 튜닝 잘못 시 DB 서버 과부하 또는 타임아웃 급증
 *
 * 면접 포인트:
 *   Q. 피크타임 결제 폭주 시 어떻게 대응했나?
 *   A. HikariCP maximumPoolSize를 CPU 코어 기반으로 계산,
 *      connectionTimeout과 keepaliveTime 조정으로 대기 오류 감소
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    @Profile("!test")
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource() {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();

        // 풀 크기 계산 예시 (4코어 서버 기준)
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int poolSize = (cpuCores * 2) + 1;

        log.info("HikariCP 풀 크기 자동 계산: CPU {} 코어 → maxPoolSize={}", cpuCores, poolSize);
        ds.setMaximumPoolSize(poolSize);
        ds.setMinimumIdle(poolSize);
        ds.setConnectionTimeout(30000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);
        ds.setKeepaliveTime(60000);
        ds.setPoolName("PGPaymentPool");

        return ds;
    }
}
