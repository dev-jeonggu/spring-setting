package com.pgpayment.worker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;

/**
 * @Async + @Scheduled 설정 — 모듈 4, 12 학습 포인트
 *
 * @Async 핵심:
 *   기본 설정: SimpleAsyncTaskExecutor → 요청마다 쓰레드 생성 → 자원 고갈 위험
 *   실무: ThreadPoolTaskExecutor를 별도 Bean으로 등록 → 쓰레드 재사용, 큐 설정
 *
 *   corePoolSize: 기본 유지 쓰레드 수
 *   maxPoolSize: 최대 쓰레드 수 (큐가 가득 찰 때 생성)
 *   queueCapacity: 큐 크기 (큐 가득 + maxPool 도달 시 RejectedExecutionException)
 *
 * 장점(@Async):
 *   - 웹훅 발송을 메인 쓰레드에서 분리 → 결제 응답 속도 향상
 *   - 웹훅 실패가 결제 트랜잭션에 영향 없음
 *
 * 단점(@Async):
 *   - MDC traceId가 쓰레드 변경 시 사라짐 → MDCTaskDecorator로 전파 필요
 *   - 예외 처리 별도 필요 (AsyncUncaughtExceptionHandler)
 *   - 같은 클래스 내 @Async 호출 → 프록시 우회 → 동작 안 함
 *
 * @Scheduled 핵심:
 *   기본: 단일 쓰레드 스케줄러 → 하나의 배치가 오래 걸리면 나머지 지연
 *   실무: ThreadPoolTaskScheduler로 멀티쓰레드 처리
 *
 * 면접 포인트:
 *   Q. @Async가 동작 안 하는 경우?
 *   A. @EnableAsync 누락, 같은 클래스 내 자기 호출, private 메서드
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    @Bean("webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("webhook-");
        executor.setTaskDecorator(new MdcTaskDecorator()); // MDC traceId 전파
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5); // 배치 5개 동시 실행 가능
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("@Async 메서드 비동기 예외: method={}, error={}", method.getName(), ex.getMessage(), ex);
    }
}
