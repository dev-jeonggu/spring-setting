# PG 결제 서비스 — Spring 설정 학습 프로젝트

다날페이 면접 대비용 Spring Boot 멀티모듈 PG 결제 서비스

## 프로젝트 구조

```
pg-payment/
├── module-api       컨트롤러, Security, Actuator, Bean Validation
├── module-core      결제 도메인, 트랜잭션, State Machine, 멱등성, 도메인 이벤트
├── module-infra     HikariCP, Redis Cache, Resilience4j, WebClient
├── module-worker    @Async 웹훅, @Scheduled 배치
└── module-support   GlobalExceptionHandler, TraceId MDC, Audit Trail, AOP
```

## 학습 모듈별 핵심 파일

| # | 설정 | 파일 | 핵심 포인트 |
|---|------|------|-------------|
| 01 | HikariCP | `DataSourceConfig.java` | maxPoolSize = (CPU * 2) + 1 |
| 02 | @Transactional | `PaymentService.java` | REQUIRES_NEW로 실패 로그 분리 |
| 03 | Spring Security | `SecurityConfig.java` | FilterChain Bean 방식, API Key + JWT |
| 04 | @Async | `AsyncConfig.java` | 전용 ThreadPoolTaskExecutor, MDC 전파 |
| 05 | @Cacheable | `MerchantCacheService.java` | CacheManager Bean 직접 등록 |
| 06 | @ControllerAdvice | `GlobalExceptionHandler.java` | PG 에러코드 체계 |
| 07 | Actuator | `PaymentSuccessRateHealthIndicator.java` | Custom HealthIndicator |
| 08 | Idempotency | `IdempotencyAspect.java` | Redis 기반 @Idempotent AOP |
| 09 | State Machine | `StatusTransitionValidator.java` | EnumMap 전이 테이블 |
| 10 | Resilience4j | `CardApiClient.java` | 카드사별 독립 Circuit Breaker |
| 11 | Audit Trail | `PaymentAuditLog.java` | 결제 상태 변경 이력 자동 기록 |
| 12 | @Scheduled | `PaymentScheduler.java` | ThreadPoolTaskScheduler, cron |
| 13 | Bean Validation | `PaymentController.java` | @Valid, @NotBlank, @Min, @Max |
| 14 | @ConfigProperties | `CardProperties.java` | @Value 대비 타입 안전, 검증 |
| 15 | @Profile | `MockCardApiClient.java` | dev/prod 환경 분리 |
| 16 | AOP Logging | `LoggingAspect.java` | 서비스 실행시간 측정 |
| 17 | WebClient | `CardClientConfig.java` | 비동기 HTTP, timeout 설정 |
| 18 | Domain Event | `PaymentApprovedEvent.java` | @TransactionalEventListener |

## 빠른 시작

### 사전 요구사항
- Java 17+
- Redis (로컬 실행: `brew install redis && redis-server`)

### 실행
```bash
./gradlew :module-api:bootRun --args='--spring.profiles.active=dev'
```

### API 테스트

**결제 승인 요청**
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: test-api-key-001" \
  -H "X-Merchant-Id: merchant-001" \
  -H "X-Idempotency-Key: order-unique-key-001" \
  -d '{
    "orderId": "ORDER-001",
    "amount": 15000,
    "cardNumber": "1234567890123456",
    "cardCompany": "shinhan"
  }'
```

**중복 결제 테스트 (멱등성)**
```bash
# 동일 X-Idempotency-Key로 두 번 요청 → 두 번째는 DUPLICATE_PAYMENT 반환
curl -X POST http://localhost:8080/api/v1/payments \
  -H "X-Api-Key: test-api-key-001" \
  -H "X-Merchant-Id: merchant-001" \
  -H "X-Idempotency-Key: order-unique-key-001" \  # 동일 키
  -d '{"orderId": "ORDER-001", "amount": 15000, "cardNumber": "1234567890123456", "cardCompany": "shinhan"}'
```

**헬스체크**
```bash
curl http://localhost:8080/actuator/health
```

**H2 콘솔 (dev)**
```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:pgpayment
```

## 각 설정의 장단점 요약

### HikariCP
- **장점**: 빠른 커넥션 획득, 풀 재사용
- **단점**: 풀 크기 튜닝 잘못 시 DB 과부하 또는 타임아웃 급증
- **면접 답변**: "CPU 코어 수 기반으로 maxPoolSize 계산, connectionTimeout 조정"

### @Transactional
- **장점**: 선언적 트랜잭션, 보일러플레이트 제거
- **단점**: self-invocation 시 동작 안 함, 과도한 트랜잭션 범위
- **면접 답변**: "REQUIRES_NEW로 실패 로그를 별개 트랜잭션으로 분리"

### Spring Security (API Key + JWT)
- **장점**: 경로별 다른 인증 방식, Stateless로 확장 용이
- **단점**: JWT 탈취 시 만료 전 무효화 어려움
- **면접 답변**: "FilterChain Bean 방식, 가맹점은 API Key, 관리자는 JWT"

### @Async
- **장점**: 웹훅 발송 비동기화 → 결제 응답 속도 향상
- **단점**: MDC 끊김, 예외 처리 별도 필요, self-invocation 동작 안 함
- **면접 답변**: "전용 ThreadPoolTaskExecutor + MdcTaskDecorator로 traceId 전파"

### @Cacheable + Redis
- **장점**: 결제마다 반복되는 가맹점 DB 조회 제거
- **단점**: 캐시 무효화 타이밍, Redis 장애 시 폴백 필요
- **면접 답변**: "CacheManager Bean 직접 등록, TTL과 직렬화 전략 명시"

### Resilience4j Circuit Breaker
- **장점**: 카드사별 독립 격리, 자동 회복
- **단점**: 임계값 튜닝 필요, OPEN 상태에서 즉시 실패
- **면접 답변**: "신한카드 장애 시 해당 CB만 OPEN, 다른 카드사 영향 없음"

### @Scheduled
- **장점**: 별도 스케줄링 서버 불필요
- **단점**: 다중 인스턴스 시 중복 실행 → ShedLock 필요
- **면접 답변**: "ThreadPoolTaskScheduler로 멀티쓰레드, ShedLock으로 중복 방지"

### @ConfigurationProperties
- **장점**: 관련 설정 객체화, 타입 안전, @Validated 검증 가능
- **단점**: @Value보다 설정 코드 많음
- **면접 답변**: "@Value는 단순하지만 타입 안전성 없음, 관련 설정은 Properties 클래스로 묶음"
