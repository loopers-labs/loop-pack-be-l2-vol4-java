package com.loopers.domain.payment;

import java.util.Optional;

public interface PaymentRepository {
    PaymentCreationResult create(Payment payment);
    Payment save(Payment payment);
    Payment completeIfPending(Payment payment);
    Optional<Payment> findByIdAndUserLoginId(Long id, String userLoginId);
    Optional<Payment> findByOrderIdAndUserLoginId(Long orderId, String userLoginId);
    Optional<Payment> findByOrderId(Long orderId);
}
