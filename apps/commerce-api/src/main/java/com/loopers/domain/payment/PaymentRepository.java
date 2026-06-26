package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> find(Long id);
    Optional<PaymentModel> findByTransactionKey(String transactionKey);
    List<PaymentModel> findByOrderId(Long orderId);
    List<PaymentModel> findByStatus(PaymentStatus status);
}
