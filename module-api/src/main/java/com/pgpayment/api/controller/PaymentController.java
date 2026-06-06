package com.pgpayment.api.controller;

import com.pgpayment.core.domain.Payment;
import com.pgpayment.core.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 결제 API 컨트롤러 — @Validated + Bean Validation 학습 포인트 (모듈 13)
 *
 * @Valid: 요청 바디 객체의 필드 검증 (MethodArgumentNotValidException 발생)
 * @Validated: 클래스 레벨에서 메서드 파라미터 검증 (ConstraintViolationException 발생)
 *
 * 장점:
 *   - 컨트롤러 진입 전 검증 → 서비스 코드 단순화
 *   - 표준 어노테이션으로 가독성 높음
 *   - GlobalExceptionHandler가 PG 에러코드로 변환
 *
 * 단점:
 *   - 복잡한 비즈니스 검증(금액 한도, 가맹점별 규칙)은 서비스에서 별도 처리 필요
 *   - @Validated 클래스 내 자기 호출 시 검증 우회
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> approve(
            @RequestHeader("X-Merchant-Id") String merchantId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        Payment payment = paymentService.approve(
                merchantId,
                request.orderId(),
                request.amount(),
                request.cardNumber(),
                idempotencyKey
        );

        return ResponseEntity.ok(Map.of(
                "paymentId", payment.getPaymentId(),
                "status", payment.getStatus(),
                "amount", payment.getAmount()
        ));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPayment(@PathVariable String paymentId) {
        Payment payment = paymentService.findById(paymentId);
        return ResponseEntity.ok(Map.of(
                "paymentId", payment.getPaymentId(),
                "status", payment.getStatus(),
                "amount", payment.getAmount(),
                "orderId", payment.getOrderId()
        ));
    }

    public record PaymentRequest(
            @NotBlank(message = "주문번호는 필수입니다")
            @Size(max = 64, message = "주문번호는 64자 이하입니다")
            String orderId,

            @NotNull(message = "결제금액은 필수입니다")
            @Min(value = 1, message = "결제금액은 1원 이상입니다")
            @Max(value = 1_000_000_000L, message = "결제금액은 10억 이하입니다")
            Long amount,

            @NotBlank(message = "카드번호는 필수입니다")
            @Pattern(regexp = "\\d{15,16}", message = "카드번호는 15~16자리 숫자입니다")
            String cardNumber,

            @NotBlank(message = "카드사 코드는 필수입니다")
            String cardCompany
    ) {}
}
