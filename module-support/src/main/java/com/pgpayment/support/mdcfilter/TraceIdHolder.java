package com.pgpayment.support.mdcfilter;

import org.slf4j.MDC;

/**
 * MDC TraceId 유틸
 *
 * MDC(Mapped Diagnostic Context): 쓰레드 로컬 기반 로그 컨텍스트
 * 장점: 별도 파라미터 전달 없이 로그에 traceId 자동 삽입
 * 단점: @Async 등 쓰레드가 바뀌는 지점에서 MDC가 끊김 → 별도 전파 필요
 */
public class TraceIdHolder {

    private static final String TRACE_ID_KEY = "traceId";

    public static void set(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    public static String get() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId != null ? traceId : "N/A";
    }

    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
