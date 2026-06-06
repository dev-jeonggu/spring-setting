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
 */
@Slf4j
@Configuration
public class DataSourceConfig {

    /**
     * HikariCP DataSource Bean
     *
     * @ConfigurationProperties(prefix = "spring.datasource.hikari"):
     *   application.yml의 spring.datasource.hikari.* 값을 HikariDataSource 필드에 자동 바인딩
     *
     * 주의: HikariCP는 spring.datasource.url이 아닌 spring.datasource.hikari.jdbc-url 키를 사용
     *   → yml에서 hikari 블록 안에 jdbc-url로 명시해야 함 (흔한 설정 실수)
     *
     * CPU 코어 기반 풀 크기 자동 계산은 ApplicationRunner에서 로그로 출력
     */
    @Bean
    @Primary
    @Profile("!test")
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource dataSource() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int poolSize = (cpuCores * 2) + 1;
        log.info("HikariCP 풀 크기 참고값: CPU {} 코어 → 권장 maxPoolSize={} (yml 설정값이 실제 적용됨)",
                cpuCores, poolSize);

        // @ConfigurationProperties가 yml 값을 자동으로 바인딩함
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }
}
