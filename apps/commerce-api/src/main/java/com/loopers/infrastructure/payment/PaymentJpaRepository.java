package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<PaymentStatus> statuses);
    Optional<Payment> findByTransactionKey(String transactionKey);
    List<Payment> findByStatusAndTransactionKeyIsNotNull(PaymentStatus status);
    List<Payment> findByStatusAndTransactionKeyIsNull(PaymentStatus status);
}
