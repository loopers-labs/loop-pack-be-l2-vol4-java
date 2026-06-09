package com.loopers.domain.payment;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    PaymentModel save(PaymentModel payment);

    Optional<PaymentModel> findByOrderId(UUID orderId);
}
