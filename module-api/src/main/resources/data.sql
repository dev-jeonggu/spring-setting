-- 개발 환경 초기 데이터

INSERT INTO merchant (merchant_id, name, api_key, webhook_url, fee_rate, active)
VALUES
    ('merchant-001', '테스트 쇼핑몰', 'test-api-key-001', 'http://localhost:8081/webhook', 0.033, true),
    ('merchant-002', '다날 테스트', 'test-api-key-002', 'http://localhost:8082/webhook', 0.025, true),
    ('merchant-003', '비활성 가맹점', 'test-api-key-003', 'http://localhost:8083/webhook', 0.030, false);
