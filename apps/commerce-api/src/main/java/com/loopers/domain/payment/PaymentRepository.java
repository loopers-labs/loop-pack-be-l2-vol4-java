package com.loopers.domain.payment;

import java.util.Optional;

public interface PaymentRepository {
    PaymentEntity save(PaymentEntity payment);
    Optional<PaymentEntity> findById(Long id);
    Optional<PaymentEntity> findByTransactionKey(String transactionKey);
    boolean existsByOrderIdAndStatusIn(Long orderId, PaymentStatus... statuses);
}
