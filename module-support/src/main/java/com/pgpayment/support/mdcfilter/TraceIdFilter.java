package com.pgpayment.support.mdcfilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 traceId를 MDC에 세팅하는 필터
 *
 * OncePerRequestFilter: 동일 요청에 한 번만 실행 보장 (forward/include 중복 방지)
 *
 * 학습 포인트:
 *   - traceId는 X-Trace-Id 헤더로 받거나 없으면 UUID 신규 생성
 *   - 응답 헤더에도 echo → 가맹점이 로그 추적 가능
 *   - finally에서 MDC.clear() 필수 → 쓰레드 풀 재사용 시 이전 값 누출 방지
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        TraceIdHolder.set(traceId);
        response.setHeader(TRACE_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIdHolder.clear(); // 쓰레드 풀 재사용 시 이전 값 누출 방지 — 필수
        }
    }
}
