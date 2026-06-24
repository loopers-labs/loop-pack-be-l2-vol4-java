package com.loopers.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findActiveByOrderId(Long orderId);
}
