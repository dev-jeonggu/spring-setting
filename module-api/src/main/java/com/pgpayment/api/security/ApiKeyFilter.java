package com.pgpayment.api.security;

import com.pgpayment.core.domain.Merchant;
import com.pgpayment.core.service.MerchantCacheService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API Key 인증 필터 — 가맹점 인증
 *
 * 요청 헤더 X-Api-Key → Redis 캐시에서 가맹점 조회
 *   → 유효하면 SecurityContext에 MERCHANT 권한 설정
 *   → 무효하면 다음 필터로 pass (JWT 필터가 처리)
 *
 * 학습 포인트:
 *   - MerchantCacheService를 통해 API Key 검증 → Redis 캐시 활용
 *   - SecurityContext 설정 → @PreAuthorize, hasRole 동작
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private final MerchantCacheService merchantCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey != null && !apiKey.isBlank()) {
            try {
                Merchant merchant = merchantCacheService.findByApiKey(apiKey);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        merchant.getMerchantId(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("API Key 인증 성공: merchantId={}", merchant.getMerchantId());
            } catch (Exception e) {
                log.warn("API Key 인증 실패: {}", e.getMessage());
                // 인증 실패해도 체인 계속 → 다른 인증 방식이 처리하거나 403 반환
            }
        }

        filterChain.doFilter(request, response);
    }
}
