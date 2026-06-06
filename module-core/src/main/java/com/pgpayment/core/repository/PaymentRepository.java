package com.pgpayment.core.repository;

import com.pgpayment.core.domain.Payment;
import com.pgpayment.core.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    List<Payment> findByMerchantId(String merchantId);

    // @Scheduled 배치에서 사용: 일정 시간 초과된 PENDING 건 조회
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt < :before")
    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime before);
}
