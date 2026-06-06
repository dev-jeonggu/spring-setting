HOST ?= localhost:8080
API_KEY ?= test-api-key-001
MERCHANT_ID ?= merchant-001

# 컬러
BOLD  := \033[1m
RESET := \033[0m
GREEN := \033[0;32m
CYAN  := \033[0;36m
YELLOW:= \033[0;33m
RED   := \033[0;31m
BLUE  := \033[0;34m

.PHONY: help up down logs demo demo-all demo-hikari demo-cache demo-statemachine \
        demo-validation demo-tracing demo-idempotency demo-security \
        pay pay-invalid health

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
help:
	@echo ""
	@echo "$(BOLD)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(RESET)"
	@echo "$(BOLD)  PG 결제 서비스 — Spring 설정 학습 데모$(RESET)"
	@echo "$(BOLD)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(RESET)"
	@echo ""
	@echo "$(CYAN)【환경 설정】$(RESET)"
	@echo "  make up          도커로 앱 + Redis 실행 (최초 1회)"
	@echo "  make down        컨테이너 종료"
	@echo "  make logs        실시간 로그 확인"
	@echo ""
	@echo "$(CYAN)【Spring 설정 데모】$(RESET)"
	@echo "  make demo-all          전체 데모 순차 실행"
	@echo "  make demo-hikari       01. HikariCP 커넥션 풀 현황"
	@echo "  make demo-cache        05. @Cacheable 캐시 히트/미스 체험"
	@echo "  make demo-statemachine 09. State Machine 전이 검증"
	@echo "  make demo-validation   13. Bean Validation 검증 실패"
	@echo "  make demo-tracing      MDC TraceId 로그 추적"
	@echo "  make demo-idempotency  08. 중복 결제 차단 (멱등성)"
	@echo "  make demo-security     03. API Key 인증 성공/실패"
	@echo ""
	@echo "$(CYAN)【결제 API 테스트】$(RESET)"
	@echo "  make pay           정상 결제 승인"
	@echo "  make pay-invalid   잘못된 요청 (Validation 실패)"
	@echo "  make health        헬스체크 (DB + Redis + 결제성공률)"
	@echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
up:
	@echo "$(BOLD)$(GREEN)▶ docker compose up 시작 (앱 + Redis)$(RESET)"
	@echo "$(YELLOW)  빌드 첫 실행은 2~5분 소요됩니다...$(RESET)"
	docker compose up -d --build
	@echo ""
	@echo "$(BOLD)$(GREEN)✅ 기동 완료$(RESET)"
	@echo "  앱:       http://localhost:8080/demo"
	@echo "  헬스체크: http://localhost:8080/actuator/health"
	@echo "  H2 콘솔:  http://localhost:8080/h2-console"
	@echo ""
	@echo "  다음 명령어로 데모를 확인하세요: $(CYAN)make demo-all$(RESET)"

down:
	docker compose down
	@echo "$(GREEN)컨테이너 종료 완료$(RESET)"

logs:
	docker compose logs -f app

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
demo-all:
	@$(MAKE) --no-print-directory _section title="전체 데모 시작"
	@$(MAKE) --no-print-directory demo-hikari
	@echo ""; sleep 1
	@$(MAKE) --no-print-directory demo-cache
	@echo ""; sleep 1
	@$(MAKE) --no-print-directory demo-statemachine
	@echo ""; sleep 1
	@$(MAKE) --no-print-directory demo-security
	@echo ""; sleep 1
	@$(MAKE) --no-print-directory demo-idempotency
	@echo ""; sleep 1
	@$(MAKE) --no-print-directory demo-validation
	@echo ""; sleep 1
	@$(MAKE) --no-print-directory demo-tracing
	@echo ""
	@echo "$(BOLD)$(GREEN)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(RESET)"
	@echo "$(BOLD)$(GREEN)  전체 데모 완료$(RESET)"
	@echo "$(BOLD)$(GREEN)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(RESET)"

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
demo-hikari:
	@$(MAKE) --no-print-directory _section title="[모듈 01] HikariCP 커넥션 풀"
	@echo "$(YELLOW)  핵심: maxPoolSize = (CPU 코어 수 × 2) + 1$(RESET)"
	@echo "$(YELLOW)  문제: 너무 크면 DB 과부하 / 너무 작으면 결제 대기 급증$(RESET)"
	@echo ""
	@curl -s http://$(HOST)/demo/hikari | python3 -m json.tool 2>/dev/null || curl -s http://$(HOST)/demo/hikari

demo-cache:
	@$(MAKE) --no-print-directory _section title="[모듈 05] @Cacheable — 캐시 히트/미스"
	@echo "$(YELLOW)  테스트: 동일 가맹점을 두 번 조회해서 응답속도 비교$(RESET)"
	@echo "$(YELLOW)  첫 호출 = DB 조회(느림) → 두 번째 = 캐시 반환(빠름)$(RESET)"
	@echo ""
	@echo "$(CYAN)① 캐시 무효화 (초기화)$(RESET)"
	@curl -s -X DELETE http://$(HOST)/demo/cache/merchant-001 | python3 -m json.tool 2>/dev/null || true
	@echo ""
	@echo "$(CYAN)② 캐시 히트/미스 비교$(RESET)"
	@curl -s http://$(HOST)/demo/cache/merchant-001 | python3 -m json.tool 2>/dev/null || curl -s http://$(HOST)/demo/cache/merchant-001

demo-statemachine:
	@$(MAKE) --no-print-directory _section title="[모듈 09] State Machine — 상태 전이 검증"
	@echo "$(YELLOW)  허용: READY→PENDING→SUCCESS→REFUND_REQUESTED→REFUNDED$(RESET)"
	@echo "$(YELLOW)  차단: SUCCESS→READY, FAILED→SUCCESS 등 역방향$(RESET)"
	@echo ""
	@curl -s http://$(HOST)/demo/statemachine | python3 -m json.tool 2>/dev/null || curl -s http://$(HOST)/demo/statemachine

demo-validation:
	@$(MAKE) --no-print-directory _section title="[모듈 13] Bean Validation — 입력값 검증"
	@echo ""
	@echo "$(CYAN)① 정상 요청 → 200 OK$(RESET)"
	@curl -s -w " [HTTP %{http_code}]" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"VALID-001","amount":15000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""
	@echo ""
	@echo "$(CYAN)② 금액 0원 → 400 BAD_REQUEST (PG 에러코드 1001)$(RESET)"
	@curl -s -w " [HTTP %{http_code}]" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"INVALID-001","amount":0,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""
	@echo ""
	@echo "$(CYAN)③ 카드번호 형식 오류 → 400 BAD_REQUEST$(RESET)"
	@curl -s -w " [HTTP %{http_code}]" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"INVALID-002","amount":10000,"cardNumber":"ABCDE","cardCompany":"shinhan"}'
	@echo ""

demo-tracing:
	@$(MAKE) --no-print-directory _section title="MDC TraceId — 요청 추적"
	@echo "$(YELLOW)  traceId가 로그와 응답 헤더에 자동 포함$(RESET)"
	@echo "$(YELLOW)  'make logs'로 서버 로그에서 [traceId] 패턴 확인$(RESET)"
	@echo ""
	@echo "$(CYAN)① 자동 생성 traceId$(RESET)"
	@curl -s -v http://$(HOST)/demo/tracing 2>&1 | grep -E "(X-Trace-Id|traceId|현재)"
	@echo ""
	@echo "$(CYAN)② 직접 지정 traceId = my-custom-trace-999$(RESET)"
	@curl -s -H 'X-Trace-Id: my-custom-trace-999' http://$(HOST)/demo/tracing | python3 -m json.tool 2>/dev/null || true

demo-idempotency:
	@$(MAKE) --no-print-directory _section title="[모듈 08] 멱등성 — 중복 결제 차단"
	@echo "$(YELLOW)  동일 X-Idempotency-Key로 두 번 요청$(RESET)"
	@echo "$(YELLOW)  Redis 연결 시: 두 번째 → DUPLICATE_PAYMENT (2001)$(RESET)"
	@echo "$(YELLOW)  Redis 미연결 시: 두 번 모두 처리 (폴백 동작)$(RESET)"
	@echo ""
	@echo "$(CYAN)① 첫 번째 결제 요청$(RESET)"
	@curl -s -w " [HTTP %{http_code}]" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -H 'X-Idempotency-Key: idem-test-fixed-key' \
	  -d '{"orderId":"IDEM-001","amount":9900,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""
	@echo ""
	@echo "$(CYAN)② 동일 키로 재요청 (중복 차단 확인)$(RESET)"
	@curl -s -w " [HTTP %{http_code}]" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -H 'X-Idempotency-Key: idem-test-fixed-key' \
	  -d '{"orderId":"IDEM-001","amount":9900,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""

demo-security:
	@$(MAKE) --no-print-directory _section title="[모듈 03] Spring Security — API Key 인증"
	@echo ""
	@echo "$(CYAN)① 올바른 API Key → 200 OK$(RESET)"
	@curl -s -w " [HTTP %{http_code}]" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: test-api-key-001' \
	  -H 'X-Merchant-Id: merchant-001' \
	  -d '{"orderId":"SEC-001","amount":5000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""
	@echo ""
	@echo "$(CYAN)② 잘못된 API Key → 403 Forbidden$(RESET)"
	@curl -s -w " [HTTP %{http_code}]" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: WRONG-API-KEY' \
	  -H 'X-Merchant-Id: merchant-001' \
	  -d '{"orderId":"SEC-002","amount":5000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""
	@echo ""
	@echo "$(CYAN)③ API Key 없음 → 403 Forbidden$(RESET)"
	@curl -s -w " [HTTP %{http_code}]" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Merchant-Id: merchant-001' \
	  -d '{"orderId":"SEC-003","amount":5000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
pay:
	@echo "$(CYAN)▶ 결제 승인 요청$(RESET)"
	@curl -s -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"ORDER-$(shell date +%s)","amount":15000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}' \
	  | python3 -m json.tool 2>/dev/null || curl -s -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"ORDER-$(shell date +%s)","amount":15000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'

pay-invalid:
	@echo "$(CYAN)▶ 잘못된 결제 요청 (금액 -1)$(RESET)"
	@curl -s -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"BAD-001","amount":-1,"cardNumber":"1234567890123456","cardCompany":"shinhan"}' \
	  | python3 -m json.tool 2>/dev/null

health:
	@echo "$(CYAN)▶ 헬스체크$(RESET)"
	@curl -s http://$(HOST)/actuator/health | python3 -m json.tool 2>/dev/null

# 내부 헬퍼
_section:
	@echo ""
	@echo "$(BOLD)$(BLUE)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(RESET)"
	@echo "$(BOLD)$(BLUE)  $(title)$(RESET)"
	@echo "$(BOLD)$(BLUE)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(RESET)"
