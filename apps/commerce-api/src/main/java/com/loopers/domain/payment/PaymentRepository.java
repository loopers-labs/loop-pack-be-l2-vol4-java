package com.loopers.domain.payment;

import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findByOrderId(Long orderId);
}
