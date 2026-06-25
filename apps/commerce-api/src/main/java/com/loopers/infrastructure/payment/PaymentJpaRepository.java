package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {
    Optional<PaymentJpaEntity> findByTransactionKey(String transactionKey);
    boolean existsByOrderIdAndStatusIn(Long orderId, PaymentStatus... statuses);
}
