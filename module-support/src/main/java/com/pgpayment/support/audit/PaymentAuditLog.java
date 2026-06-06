package com.pgpayment.support.audit;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Audit Trail — 결제 상태 변경 이력 테이블
 *
 * 장점:
 *   - 누가/언제/어떤 상태로 바꿨는지 추적 가능 (금융 감사 필수)
 *   - @EntityListeners로 자동 기록 → 서비스 코드 오염 없음
 *
 * 단점:
 *   - 결제 1건당 N개 audit log 발생 → 테이블 크기 빠르게 증가
 *   - 쓰기 부하 증가 → 파티셔닝 / 아카이빙 전략 필요
 */
@Entity
@Table(name = "payment_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String paymentId;

    @Column(nullable = false)
    private String previousStatus;

    @Column(nullable = false)
    private String newStatus;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    private String traceId;

    private String changedBy;

    @Builder
    public PaymentAuditLog(String paymentId, String previousStatus,
                           String newStatus, String traceId, String changedBy) {
        this.paymentId = paymentId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.newStatus = newStatus;
        this.changedAt = LocalDateTime.now();
        this.traceId = traceId;
        this.changedBy = changedBy;
    }
}
