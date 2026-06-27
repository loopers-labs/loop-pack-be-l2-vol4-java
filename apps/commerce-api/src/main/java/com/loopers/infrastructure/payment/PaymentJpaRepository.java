package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByTransactionKey(String transactionKey);
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
    List<PaymentEntity> findAllByStatusIn(List<PaymentStatus> statuses);
}