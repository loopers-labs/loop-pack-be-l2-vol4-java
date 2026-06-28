package com.loopers.application.payment;

import com.loopers.domain.payment.PaymentModel;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    Optional<PaymentModel> findByOrderId(Long orderId);
    java.util.List<PaymentModel> findAllByStatusAndCreatedAtBefore(com.loopers.domain.payment.PaymentStatus status, java.time.ZonedDateTime time);
}
