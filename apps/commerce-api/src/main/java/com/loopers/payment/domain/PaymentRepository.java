package com.loopers.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByTransactionKey(String transactionKey);

    Optional<Payment> findActiveByOrderNumber(String orderNumber);
}
