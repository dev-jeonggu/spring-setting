package com.pgpayment.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 인증 필터 — 관리자 인증
 *
 * Authorization: Bearer {token} 헤더에서 JWT 추출 → 검증 → SecurityContext 설정
 *
 * JWT 구조: Header.Payload.Signature
 *   - Header: 알고리즘 (HS256)
 *   - Payload: 클레임 (sub, role, exp)
 *   - Signature: 서버 비밀키로 서명 → 위변조 방지
 *
 * 장점:
 *   - Stateless → 서버 세션 없음 → 수평 확장 유리
 *   - 클라이언트가 정보 포함 → DB 조회 없이 인증 가능
 *
 * 단점:
 *   - 토큰 탈취 시 만료 전까지 무효화 불가
 *     → Redis 블랙리스트, 짧은 만료 + Refresh Token 전략 필요
 *   - Payload 암호화 아님 → 민감 정보 저장 금지
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${jwt.secret:pg-payment-secret-key-must-be-256-bits-long!}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String role = claims.get("role", String.class);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JWT 인증 성공: subject={}, role={}", claims.getSubject(), role);
            } catch (Exception e) {
                log.warn("JWT 인증 실패: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
