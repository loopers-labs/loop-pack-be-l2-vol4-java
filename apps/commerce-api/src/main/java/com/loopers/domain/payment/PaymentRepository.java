package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    Optional<PaymentModel> findByOrderId(Long orderId);
    Optional<PaymentModel> findByTransactionKey(String transactionKey);
    List<PaymentModel> findPendingBefore(ZonedDateTime threshold);
}
