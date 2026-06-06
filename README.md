# PG 결제 서비스 — Spring 설정 학습 프로젝트

Spring Boot 멀티모듈로 구성한 PG(결제대행) 서비스입니다.  
각 Spring 설정의 **동작 원리와 장단점**을 코드와 데모 API로 직접 확인할 수 있습니다.

---

## 실행 방법

**Docker만 있으면 됩니다.**

```bash
make up       # 앱 + Redis 빌드 및 실행 (최초 2~5분 소요)
make demo-all # 전체 데모 순차 실행
```

종료할 때는 `make down`

---

## 데모 명령어

```bash
make help              # 전체 명령어 목록
make demo-all          # 전체 데모 한 번에 실행

make demo-hikari       # 01. HikariCP 커넥션 풀 현황
make demo-cache        # 05. @Cacheable 캐시 히트/미스 속도 비교
make demo-statemachine # 09. 결제 상태 전이 허용/차단 표
make demo-security     # 03. API Key 인증 성공/실패
make demo-idempotency  # 08. 중복 결제 차단 (멱등성)
make demo-validation   # 13. Bean Validation 오류 케이스
make demo-tracing      # MDC TraceId 로그 추적

make pay               # 결제 승인 요청
make pay-invalid       # 잘못된 요청 (검증 실패)
make health            # 헬스체크 요약
make logs              # 실시간 서버 로그
```

---

## 브라우저에서 확인

| URL | 설명 |
|-----|------|
| `http://localhost:8080/demo` | 데모 엔드포인트 목록 |
| `http://localhost:8080/demo/hikari` | HikariCP 풀 현황 |
| `http://localhost:8080/demo/statemachine` | 상태 전이 검증 결과 |
| `http://localhost:8080/demo/cache/merchant-001` | 캐시 히트/미스 |
| `http://localhost:8080/actuator/health` | 헬스체크 (JSON) |
| `http://localhost:8080/h2-console` | H2 DB 콘솔 |

> H2 콘솔 접속 정보: JDBC URL `jdbc:h2:mem:pgpayment` / 계정 `sa` / 비밀번호 없음

---

## 프로젝트 구조

```
pg-payment/
├── module-api      컨트롤러, Spring Security, Actuator, Bean Validation
├── module-core     결제 도메인, @Transactional, State Machine, 멱등성 AOP, 도메인 이벤트
├── module-infra    HikariCP, Redis Cache, Resilience4j, WebClient, @ConfigurationProperties
├── module-worker   @Async 웹훅, @Scheduled 배치, MDC 전파
└── module-support  GlobalExceptionHandler, TraceId MDC, Audit Trail, LoggingAspect
```

---

## 모듈별 핵심 파일

| # | 설정 | 파일 |
|---|------|------|
| 01 | HikariCP | [DataSourceConfig.java](module-infra/src/main/java/com/pgpayment/infra/config/DataSourceConfig.java) |
| 02 | @Transactional | [PaymentService.java](module-core/src/main/java/com/pgpayment/core/service/PaymentService.java) |
| 03 | Spring Security | [SecurityConfig.java](module-api/src/main/java/com/pgpayment/api/security/SecurityConfig.java) |
| 04 | @Async | [AsyncConfig.java](module-worker/src/main/java/com/pgpayment/worker/config/AsyncConfig.java) |
| 05 | @Cacheable + Redis | [MerchantCacheService.java](module-core/src/main/java/com/pgpayment/core/service/MerchantCacheService.java) |
| 06 | @ControllerAdvice | [GlobalExceptionHandler.java](module-support/src/main/java/com/pgpayment/support/exception/GlobalExceptionHandler.java) |
| 07 | Actuator | [PaymentSuccessRateHealthIndicator.java](module-api/src/main/java/com/pgpayment/api/health/PaymentSuccessRateHealthIndicator.java) |
| 08 | 멱등성 AOP | [IdempotencyAspect.java](module-core/src/main/java/com/pgpayment/core/idempotency/IdempotencyAspect.java) |
| 09 | State Machine | [StatusTransitionValidator.java](module-core/src/main/java/com/pgpayment/core/statemachine/StatusTransitionValidator.java) |
| 10 | Resilience4j | [CardApiClient.java](module-infra/src/main/java/com/pgpayment/infra/client/CardApiClient.java) |
| 11 | Audit Trail | [PaymentAuditLog.java](module-support/src/main/java/com/pgpayment/support/audit/PaymentAuditLog.java) |
| 12 | @Scheduled | [PaymentScheduler.java](module-worker/src/main/java/com/pgpayment/worker/scheduler/PaymentScheduler.java) |
| 13 | Bean Validation | [PaymentController.java](module-api/src/main/java/com/pgpayment/api/controller/PaymentController.java) |
| 14 | @ConfigurationProperties | [CardProperties.java](module-infra/src/main/java/com/pgpayment/infra/properties/CardProperties.java) |
| 15 | @Profile | [MockCardApiClient.java](module-infra/src/main/java/com/pgpayment/infra/client/MockCardApiClient.java) |
| 16 | AOP 로깅 | [LoggingAspect.java](module-support/src/main/java/com/pgpayment/support/aop/LoggingAspect.java) |
| 17 | WebClient | [CardClientConfig.java](module-infra/src/main/java/com/pgpayment/infra/config/CardClientConfig.java) |
| 18 | 도메인 이벤트 | [PaymentApprovedEvent.java](module-core/src/main/java/com/pgpayment/core/event/PaymentApprovedEvent.java) |

---

## 각 설정 장단점 요약

### HikariCP
- **장점** 커넥션 재사용으로 매 요청마다 TCP 연결 오버헤드 제거
- **단점** maxPoolSize 잘못 설정 시 DB 과부하 또는 피크 시 대기 급증

### @Transactional
- **장점** 선언적 트랜잭션으로 보일러플레이트 제거
- **단점** 같은 클래스 내 자기 호출(self-invocation) 시 동작 안 함

### Spring Security (API Key + JWT)
- **장점** 경로별 다른 인증 방식 적용, Stateless로 서버 확장 용이
- **단점** JWT 탈취 시 만료 전 무효화 불가 → Redis 블랙리스트 필요

### @Async
- **장점** 웹훅 발송을 별도 쓰레드로 분리 → 결제 응답 속도 향상
- **단점** 쓰레드 전환 시 MDC traceId 끊김 → MdcTaskDecorator 필요

### @Cacheable + Redis
- **장점** 결제마다 반복되는 가맹점 DB 조회를 캐시로 대체
- **단점** 데이터 변경 시 @CacheEvict 호출 필수, Redis 장애 시 폴백 필요

### Resilience4j Circuit Breaker
- **장점** 카드사별 독립 CB로 장애 격리, 자동 회복
- **단점** 슬라이딩 윈도우·실패율 임계값 튜닝 필요

### @Scheduled
- **장점** 별도 스케줄링 서버 없이 배치 처리
- **단점** 다중 인스턴스 배포 시 중복 실행 → ShedLock 라이브러리 필요

### @ConfigurationProperties
- **장점** 관련 설정을 객체로 묶음, @Validated로 타입 검증 가능
- **단점** @Value보다 설정 코드가 많음, 단순한 값 하나에는 과함

### 멱등성 AOP (@Idempotent)
- **장점** 네트워크 재시도로 인한 중복 결제 완전 차단
- **단점** Redis 장애 시 검증 불가 → 폴백 전략 필요

### State Machine
- **장점** 허용된 상태 전이만 통과 → 데이터 정합성 보장
- **단점** 상태 추가 시 전이 테이블 수동 관리 필요

---

## 테스트 계정

| 가맹점 ID | API Key | 상태 |
|-----------|---------|------|
| merchant-001 | test-api-key-001 | 활성 |
| merchant-002 | test-api-key-002 | 활성 |
| merchant-003 | test-api-key-003 | 비활성 |

---

## 기술 스택

- Java 17 / Spring Boot 3.2.5
- H2 (dev) / MySQL (prod)
- Redis 7
- Resilience4j 2.1
- JWT (jjwt 0.12)
