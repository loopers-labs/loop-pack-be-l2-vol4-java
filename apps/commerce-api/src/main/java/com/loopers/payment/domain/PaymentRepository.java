package com.loopers.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Payment saveAndFlush(Payment payment);

    Optional<Payment> findById(Long paymentId);

    Optional<Payment> findByPgTransactionKey(String pgTransactionKey);

    Optional<Payment> findLatestByOrderId(Long orderId);
}
