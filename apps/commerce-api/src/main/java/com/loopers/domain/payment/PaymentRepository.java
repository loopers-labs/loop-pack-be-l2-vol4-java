package com.loopers.domain.payment;

import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    Optional<PaymentModel> findByOrderId(Long orderId);
    Optional<PaymentModel> findByPgTransactionId(String pgTransactionId);
}
