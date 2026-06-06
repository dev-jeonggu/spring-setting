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
	@curl -s http://$(HOST)/demo/hikari

demo-cache:
	@curl -s -X DELETE http://$(HOST)/demo/cache/merchant-001
	@curl -s http://$(HOST)/demo/cache/merchant-001

demo-statemachine:
	@curl -s http://$(HOST)/demo/statemachine

demo-validation:
	@curl -s http://$(HOST)/demo/validation
	@echo ""
	@echo "$(CYAN)── 실제 실행 결과 ──────────────────────────────────$(RESET)"
	@echo "$(CYAN)① 정상 요청$(RESET)"
	@curl -s -w "   → HTTP %{http_code}\n" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"VALID-001","amount":15000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""
	@echo "$(CYAN)② 금액 0원$(RESET)"
	@curl -s -w "   → HTTP %{http_code}\n" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"INVALID-001","amount":0,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""
	@echo "$(CYAN)③ 카드번호 형식 오류$(RESET)"
	@curl -s -w "   → HTTP %{http_code}\n" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"INVALID-002","amount":10000,"cardNumber":"ABCDE","cardCompany":"shinhan"}'
	@echo ""

demo-tracing:
	@curl -s -H 'X-Trace-Id: my-custom-trace-999' http://$(HOST)/demo/tracing

demo-idempotency:
	@$(MAKE) --no-print-directory _section title="[모듈 08] 멱등성 — 중복 결제 차단"
	@echo "$(YELLOW)  동일 X-Idempotency-Key로 두 번 요청$(RESET)"
	@echo "$(YELLOW)  Redis 연결 시: 두 번째 → DUPLICATE_PAYMENT (2001)$(RESET)"
	@echo ""
	@echo "$(CYAN)① 첫 번째 결제 요청$(RESET)"
	@curl -s -w "   → HTTP %{http_code}\n" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -H 'X-Idempotency-Key: idem-test-fixed-key' \
	  -d '{"orderId":"IDEM-001","amount":9900,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""
	@echo "$(CYAN)② 동일 키로 재요청 → 중복 차단 확인$(RESET)"
	@curl -s -w "   → HTTP %{http_code}\n" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -H 'X-Idempotency-Key: idem-test-fixed-key' \
	  -d '{"orderId":"IDEM-001","amount":9900,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""

demo-security:
	@$(MAKE) --no-print-directory _section title="[모듈 03] Spring Security — API Key 인증"
	@echo "$(CYAN)① 올바른 API Key$(RESET)"
	@curl -s -w "   → HTTP %{http_code}\n" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: test-api-key-001' \
	  -H 'X-Merchant-Id: merchant-001' \
	  -d '{"orderId":"SEC-001","amount":5000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""
	@echo "$(CYAN)② 잘못된 API Key$(RESET)"
	@curl -s -w "   → HTTP %{http_code}\n" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: WRONG-API-KEY' \
	  -H 'X-Merchant-Id: merchant-001' \
	  -d '{"orderId":"SEC-002","amount":5000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""
	@echo "$(CYAN)③ API Key 없음$(RESET)"
	@curl -s -w "   → HTTP %{http_code}\n" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Merchant-Id: merchant-001' \
	  -d '{"orderId":"SEC-003","amount":5000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'
	@echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
pay:
	@curl -s -w "\n  → HTTP %{http_code}\n" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"ORDER-$(shell date +%s)","amount":15000,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'

pay-invalid:
	@curl -s -w "\n  → HTTP %{http_code}\n" -X POST http://$(HOST)/api/v1/payments \
	  -H 'Content-Type: application/json' \
	  -H 'X-Api-Key: $(API_KEY)' \
	  -H 'X-Merchant-Id: $(MERCHANT_ID)' \
	  -d '{"orderId":"BAD-001","amount":-1,"cardNumber":"1234567890123456","cardCompany":"shinhan"}'

health:
	@curl -s http://$(HOST)/actuator/health \
	  | python3 -c "import sys,json; d=json.load(sys.stdin); \
	    print('\n  전체 상태:', d['status']); \
	    [print(f'  {k:20s} {v[\"status\"]}') for k,v in d.get('components',{}).items()]; print()"

# 내부 헬퍼
_section:
	@echo ""
	@echo "$(BOLD)$(BLUE)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(RESET)"
	@echo "$(BOLD)$(BLUE)  $(title)$(RESET)"
	@echo "$(BOLD)$(BLUE)━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━$(RESET)"
