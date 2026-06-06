package com.pgpayment.worker.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * MDC 컨텍스트를 @Async 쓰레드로 전파하는 데코레이터
 *
 * 문제: @Async는 새 쓰레드에서 실행 → MDC.get("traceId") = null
 * 해결: 실행 전 MDC 복사, 실행 후 정리
 *
 * 이 설정 없으면 웹훅 로그에서 traceId가 사라짐 → 운영 추적 불가
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
