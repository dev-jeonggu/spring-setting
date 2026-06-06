package com.pgpayment.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 설정 — 모듈 3 학습 포인트
 *
 * FilterChain Bean 방식 (Spring Boot 3.x 표준):
 *   이전: WebSecurityConfigurerAdapter 상속 (deprecated)
 *   현재: SecurityFilterChain Bean 직접 등록
 *
 * 이중 인증 구조:
 *   가맹점 → API Key 인증 (ApiKeyFilter)
 *   관리자 → JWT 인증 (JwtAuthFilter)
 *
 * 장점:
 *   - 요청 경로별 다른 인증 방식 적용 가능
 *   - 각 Filter가 독립적 → 테스트, 수정 용이
 *   - Stateless → 서버 확장에 유리
 *
 * 단점:
 *   - 필터 체인 순서 관리 필요 (addFilterBefore/After)
 *   - JWT 탈취 시 만료 전까지 무효화 어려움 (Redis 블랙리스트 필요)
 *
 * 면접 포인트:
 *   Q. API Key와 JWT를 동시에 지원하는 구조를 어떻게 만들었나?
 *   A. SecurityFilterChain에서 경로별 분기,
 *      /api/v1/** → ApiKeyFilter, /admin/** → JwtAuthFilter
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ApiKeyFilter apiKeyFilter;
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // REST API — CSRF 불필요
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").hasRole("MERCHANT")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, ApiKeyFilter.class)
                .build();
    }
}
