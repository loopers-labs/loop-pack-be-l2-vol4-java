package com.loopers.domain.payment;

import java.util.Optional;

public interface PaymentRepository {
    PaymentEntity save(PaymentEntity payment);
    Optional<PaymentEntity> findById(String id);
    Optional<PaymentEntity> findByTransactionKey(String transactionKey);
    boolean existsByOrderIdAndStatusIn(String orderId, PaymentStatus... statuses);
}
