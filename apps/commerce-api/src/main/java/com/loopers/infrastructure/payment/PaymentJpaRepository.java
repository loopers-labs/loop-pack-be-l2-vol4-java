package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByTransactionKey(String transactionKey);
    List<PaymentEntity> findByOrderId(Long orderId);
    List<PaymentEntity> findByStatus(PaymentStatus status);
}
