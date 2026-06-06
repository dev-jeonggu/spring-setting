package com.pgpayment.core.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Payment 엔티티
 *
 * @EntityListeners(AuditingEntityListener): Spring Data JPA Auditing
 *   장점: createdAt/updatedAt 자동 관리 → 코드 중복 제거
 *   단점: @EnableJpaAuditing 설정 필요, 테스트 시 별도 설정 필요
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @Column(name = "payment_id")
    private String paymentId;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String merchantId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String cardNumber;

    private String failureReason;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder
    public Payment(String paymentId, String orderId, String merchantId,
                   Long amount, String cardNumber) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.cardNumber = cardNumber;
        this.status = PaymentStatus.READY;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void transition(PaymentStatus newStatus) {
        this.status = newStatus;
    }

    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }
}
