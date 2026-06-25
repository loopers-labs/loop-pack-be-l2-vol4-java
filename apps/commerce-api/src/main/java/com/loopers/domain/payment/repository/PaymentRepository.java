package com.loopers.domain.payment.repository;

import com.loopers.domain.payment.model.Payment;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(Long id);
    Optional<Payment> findByTransactionKey(String transactionKey);
    boolean existsActiveByOrderId(Long orderId);
}
