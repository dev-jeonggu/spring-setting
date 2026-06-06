package com.pgpayment.api.demo;

import com.pgpayment.core.domain.Merchant;
import com.pgpayment.core.domain.PaymentStatus;
import com.pgpayment.core.service.MerchantCacheService;
import com.pgpayment.core.statemachine.StatusTransitionValidator;
import com.pgpayment.support.exception.PgException;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DataSource dataSource;
    private final MerchantCacheService merchantCacheService;
    private final StatusTransitionValidator transitionValidator;

    private static final String DIV = "─".repeat(54);

    private String header(String title) {
        return "\n╔" + "═".repeat(54) + "╗\n"
             + "║  " + padRight(title, 52) + "║\n"
             + "╚" + "═".repeat(54) + "╝\n";
    }

    private String section(String title) {
        int pad = Math.max(0, 50 - title.length());
        return "\n┌─ " + title + " " + "─".repeat(pad) + "┐\n";
    }

    private String row(String label, String value) {
        return String.format("  %-20s %s\n", label, value);
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    // ──────────────────────────────────────────────────────────
    // 인덱스
    // ──────────────────────────────────────────────────────────
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String index() {
        return header("PG 결제 서비스 — Spring 설정 학습 데모")
             + "  " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n"
             + section("데모 엔드포인트")
             + row("GET /demo/hikari",       "HikariCP 커넥션 풀 현황")
             + row("GET /demo/cache/{id}",   "캐시 히트/미스 체험 (id: merchant-001)")
             + row("DEL /demo/cache/{id}",   "캐시 무효화")
             + row("GET /demo/statemachine", "결제 상태 전이 허용/차단 검증")
             + row("GET /demo/validation",   "Bean Validation 케이스별 안내")
             + row("GET /demo/tracing",      "MDC TraceId 체험")
             + "\n"
             + section("결제 API")
             + row("POST /api/v1/payments",  "결제 승인 (X-Api-Key 필요)")
             + row("GET  /actuator/health",  "헬스체크 (JSON)")
             + row("GET  /h2-console",       "H2 DB 콘솔 (브라우저)")
             + "\n"
             + section("테스트 API Key")
             + row("merchant-001 (활성)",    "test-api-key-001")
             + row("merchant-002 (활성)",    "test-api-key-002")
             + row("merchant-003 (비활성)",  "test-api-key-003")
             + "\n  → make help   로 Makefile 데모 명령어 확인\n";
    }

    // ──────────────────────────────────────────────────────────
    // 1. HikariCP
    // ──────────────────────────────────────────────────────────
    @GetMapping(value = "/hikari", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String hikari() {
        StringBuilder sb = new StringBuilder();
        sb.append(header("[모듈 01] HikariCP 커넥션 풀"));

        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            int cpu = Runtime.getRuntime().availableProcessors();

            sb.append(section("실시간 풀 현황"));
            sb.append(row("풀 이름",       hikari.getPoolName()));
            sb.append(row("최대 커넥션 수", hikari.getMaximumPoolSize() + "개"));
            sb.append(row("활성 커넥션",   (pool != null ? pool.getActiveConnections() : "?") + "개  ← 현재 쿼리 실행 중"));
            sb.append(row("대기 커넥션",   (pool != null ? pool.getIdleConnections()   : "?") + "개  ← 재사용 대기"));
            sb.append(row("전체 커넥션",   (pool != null ? pool.getTotalConnections()   : "?") + "개"));
            sb.append(row("대기 쓰레드",   (pool != null ? pool.getThreadsAwaitingConnection() : "?") + "개  ← 이게 늘면 풀 부족 신호"));
            sb.append(row("연결 타임아웃", hikari.getConnectionTimeout() / 1000 + "초"));
            sb.append(row("최대 수명",     hikari.getMaxLifetime() / 1000 + "초"));

            sb.append(section("풀 크기 계산식"));
            sb.append(String.format("  maxPoolSize = (CPU 코어 수 × 2) + 1\n"));
            sb.append(String.format("              = (%d × 2) + 1 = %d  (현재 서버 기준)\n", cpu, cpu * 2 + 1));
            sb.append(String.format("  현재 설정값 = %d개  (docker 환경 고정)\n", hikari.getMaximumPoolSize()));
        }

        sb.append(section("장점 vs 단점"));
        sb.append("  ✅ 장점  커넥션 재사용으로 매 요청마다 TCP 연결 오버헤드 제거\n");
        sb.append("  ✅ 장점  풀 고갈 시 대기 큐로 순서 보장\n");
        sb.append("  ❌ 단점  maxPoolSize 과대 설정 → DB 서버 커넥션 한도 초과\n");
        sb.append("  ❌ 단점  maxPoolSize 과소 설정 → 피크 시 대기 급증, 타임아웃\n");

        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────
    // 2. @Cacheable
    // ──────────────────────────────────────────────────────────
    @GetMapping(value = "/cache/{merchantId}", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String cache(@PathVariable String merchantId) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("[모듈 05] @Cacheable — 캐시 히트/미스"));

        sb.append(section("실험"));
        sb.append("  동일한 가맹점을 두 번 조회해서 응답 속도를 비교합니다.\n\n");

        try {
            long s1 = System.currentTimeMillis();
            Merchant m = merchantCacheService.findById(merchantId);
            long t1 = System.currentTimeMillis() - s1;

            long s2 = System.currentTimeMillis();
            merchantCacheService.findById(merchantId);
            long t2 = System.currentTimeMillis() - s2;

            sb.append(String.format("  1번째 호출  %3dms  ← DB 조회 (캐시 미스)\n", t1));
            sb.append(String.format("  2번째 호출  %3dms  ← 캐시 반환 (캐시 히트)\n", t2));
            sb.append(String.format("\n  속도 향상   %.1f배 빠름\n", t1 > 0 ? (double) t1 / Math.max(t2, 1) : 1.0));
            sb.append(String.format("  가맹점명    %s\n", m.getName()));
            sb.append(String.format("  캐시 키     merchant::%s\n", merchantId));

            sb.append(section("장점 vs 단점"));
            sb.append("  ✅ 장점  결제마다 반복되는 가맹점 DB 조회 → Redis 캐시로 대체\n");
            sb.append("  ✅ 장점  TTL 설정으로 자동 만료 (1시간)\n");
            sb.append("  ❌ 단점  가맹점 정보 변경 시 @CacheEvict 호출 필수\n");
            sb.append("  ❌ 단점  Redis 장애 시 캐시 전체 불가 → 폴백 전략 필요\n");

            sb.append("\n  → DELETE /demo/cache/" + merchantId + "  으로 캐시 무효화 후 재시도\n");

        } catch (Exception e) {
            sb.append("  ⚠️  오류: ").append(e.getMessage()).append("\n");
        }

        return sb.toString();
    }

    @DeleteMapping(value = "/cache/{merchantId}", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String evictCache(@PathVariable String merchantId) {
        merchantCacheService.evictAll();
        return "\n  ✅ 캐시 무효화 완료\n\n"
             + "  → GET /demo/cache/" + merchantId + "  재호출 시 DB 조회 발생 확인\n\n";
    }

    // ──────────────────────────────────────────────────────────
    // 3. State Machine
    // ──────────────────────────────────────────────────────────
    @GetMapping(value = "/statemachine", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String stateMachine() {
        StringBuilder sb = new StringBuilder();
        sb.append(header("[모듈 09] State Machine — 상태 전이 검증"));

        sb.append(section("허용 전이 규칙"));
        sb.append("  READY → PENDING → SUCCESS → REFUND_REQUESTED → REFUNDED\n");
        sb.append("                       ↘ FAILED\n");

        sb.append(section("전이 검증 결과"));
        sb.append("  " + DIV + "\n");
        sb.append(String.format("  %-38s %s\n", "전이", "결과"));
        sb.append("  " + DIV + "\n");

        Object[][] cases = {
            {"READY     → PENDING",          PaymentStatus.READY,            PaymentStatus.PENDING},
            {"PENDING   → SUCCESS",          PaymentStatus.PENDING,          PaymentStatus.SUCCESS},
            {"SUCCESS   → REFUND_REQUESTED", PaymentStatus.SUCCESS,          PaymentStatus.REFUND_REQUESTED},
            {"REFUND_REQUESTED → REFUNDED",  PaymentStatus.REFUND_REQUESTED, PaymentStatus.REFUNDED},
            {"SUCCESS   → READY   (역방향)", PaymentStatus.SUCCESS,          PaymentStatus.READY},
            {"FAILED    → SUCCESS (역방향)", PaymentStatus.FAILED,           PaymentStatus.SUCCESS},
            {"REFUNDED  → PENDING (역방향)", PaymentStatus.REFUNDED,         PaymentStatus.PENDING},
        };

        for (Object[] c : cases) {
            String label = (String) c[0];
            PaymentStatus from = (PaymentStatus) c[1];
            PaymentStatus to   = (PaymentStatus) c[2];
            try {
                transitionValidator.validate(from, to);
                sb.append(String.format("  %-38s ✅ 허용\n", label));
            } catch (PgException e) {
                sb.append(String.format("  %-38s ❌ 차단  [%s]\n", label, e.getErrorCode().getCode()));
            }
        }
        sb.append("  " + DIV + "\n");

        sb.append(section("장점 vs 단점"));
        sb.append("  ✅ 장점  허용된 전이만 통과 → 데이터 정합성 보장\n");
        sb.append("  ✅ 장점  비즈니스 규칙을 코드로 명시화\n");
        sb.append("  ❌ 단점  상태 추가 시 전이 테이블 수동 관리 필요\n");
        sb.append("  ❌ 단점  복잡한 도메인은 Spring State Machine 라이브러리 검토\n");

        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────
    // 4. Bean Validation
    // ──────────────────────────────────────────────────────────
    @GetMapping(value = "/validation", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String validation() {
        String host = "http://localhost:8080";
        StringBuilder sb = new StringBuilder();
        sb.append(header("[모듈 13] Bean Validation — 입력값 검증"));
        sb.append("  아래 curl을 직접 실행해서 결과를 확인하세요.\n");

        sb.append(section("케이스 ① 정상 요청 → 200 OK"));
        sb.append("  curl -s -X POST " + host + "/api/v1/payments \\\n");
        sb.append("    -H 'X-Api-Key: test-api-key-001' \\\n");
        sb.append("    -H 'X-Merchant-Id: merchant-001' \\\n");
        sb.append("    -H 'Content-Type: application/json' \\\n");
        sb.append("    -d '{\"orderId\":\"ORDER-1\",\"amount\":15000,\n");
        sb.append("         \"cardNumber\":\"1234567890123456\",\"cardCompany\":\"shinhan\"}'\n");

        sb.append(section("케이스 ② 금액 0원 → 400 (에러코드 1001)"));
        sb.append("  @Min(1) 위반 → MethodArgumentNotValidException\n");
        sb.append("              → GlobalExceptionHandler → PG 에러코드 1001\n\n");
        sb.append("  curl -s -X POST " + host + "/api/v1/payments \\\n");
        sb.append("    -H 'X-Api-Key: test-api-key-001' \\\n");
        sb.append("    -H 'X-Merchant-Id: merchant-001' \\\n");
        sb.append("    -H 'Content-Type: application/json' \\\n");
        sb.append("    -d '{\"orderId\":\"ORDER-2\",\"amount\":0,\n");
        sb.append("         \"cardNumber\":\"1234567890123456\",\"cardCompany\":\"shinhan\"}'\n");

        sb.append(section("케이스 ③ 카드번호 형식 오류 → 400"));
        sb.append("  @Pattern(regexp=\"\\\\d{15,16}\") 위반\n\n");
        sb.append("  curl -s -X POST " + host + "/api/v1/payments \\\n");
        sb.append("    -H 'X-Api-Key: test-api-key-001' \\\n");
        sb.append("    -H 'X-Merchant-Id: merchant-001' \\\n");
        sb.append("    -H 'Content-Type: application/json' \\\n");
        sb.append("    -d '{\"orderId\":\"ORDER-3\",\"amount\":10000,\n");
        sb.append("         \"cardNumber\":\"ABCDE\",\"cardCompany\":\"shinhan\"}'\n");

        sb.append(section("장점 vs 단점"));
        sb.append("  ✅ 장점  컨트롤러 진입 전 검증 → 서비스 코드 단순화\n");
        sb.append("  ✅ 장점  GlobalExceptionHandler가 PG 에러코드로 일괄 변환\n");
        sb.append("  ❌ 단점  복잡한 비즈니스 규칙(가맹점별 한도)은 서비스에서 별도 처리\n");

        sb.append(section("Makefile 단축 명령어"));
        sb.append("  make demo-validation  ← 세 케이스 자동 실행\n");

        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────
    // 5. MDC TraceId
    // ──────────────────────────────────────────────────────────
    @GetMapping(value = "/tracing", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String tracing(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("MDC TraceId — 요청 추적"));

        sb.append(section("현재 요청"));
        if (traceId != null) {
            sb.append("  X-Trace-Id 헤더 직접 지정: " + traceId + "\n");
        } else {
            sb.append("  X-Trace-Id 헤더 없음 → 서버가 UUID 자동 생성\n");
        }
        sb.append("  응답 헤더에 X-Trace-Id가 포함되어 반환됩니다.\n");

        sb.append(section("사용법"));
        sb.append("  # 자동 생성\n");
        sb.append("  curl -v http://localhost:8080/demo/tracing 2>&1 | grep X-Trace-Id\n\n");
        sb.append("  # 직접 지정\n");
        sb.append("  curl -H 'X-Trace-Id: my-trace-001' http://localhost:8080/demo/tracing\n\n");
        sb.append("  # 서버 로그에서 확인\n");
        sb.append("  make logs | grep traceId\n");

        sb.append(section("장점 vs 단점"));
        sb.append("  ✅ 장점  파라미터 전달 없이 모든 로그에 traceId 자동 삽입\n");
        sb.append("  ✅ 장점  가맹점이 X-Trace-Id로 문의 시 즉시 로그 추적 가능\n");
        sb.append("  ❌ 단점  @Async 쓰레드 전환 시 MDC 끊김\n");
        sb.append("           → MdcTaskDecorator로 컨텍스트 복사 필요\n");
        sb.append("  ❌ 단점  ThreadPool 재사용 시 MDC.clear() 누락 시 이전 값 누출\n");
        sb.append("           → TraceIdFilter finally 블록에서 반드시 clear()\n");

        return sb.toString();
    }
}
