package com.loopers.payment.domain;

import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findByTransactionKey(String transactionKey);
}
