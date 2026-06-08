package com.loopers.domain.payment;

import com.loopers.domain.payment.enums.PaymentStatus;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    List<PaymentModel> findAllByOrderId(Long orderId);
    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
