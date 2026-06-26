package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentCreationResult create(Payment payment);
    Payment save(Payment payment);
    Payment completeIfPending(Payment payment);
    List<Payment> findPendingPaymentsForReconciliation(int limit);
    Optional<Payment> findByIdAndUserLoginId(Long id, String userLoginId);
    Optional<Payment> findByOrderIdAndUserLoginId(Long orderId, String userLoginId);
    Optional<Payment> findByOrderId(Long orderId);
}
