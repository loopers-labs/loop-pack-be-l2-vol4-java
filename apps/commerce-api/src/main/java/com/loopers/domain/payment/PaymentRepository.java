package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(Long id);
    Optional<Payment> findByTransactionKey(String transactionKey);
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
    List<Payment> findAllByStatusIn(List<PaymentStatus> statuses);
}