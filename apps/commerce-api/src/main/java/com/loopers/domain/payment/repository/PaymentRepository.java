package com.loopers.domain.payment.repository;

import com.loopers.domain.payment.model.Payment;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(Long id);
    boolean existsActiveByOrderId(Long orderId);
}
