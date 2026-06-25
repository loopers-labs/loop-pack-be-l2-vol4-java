package com.loopers.domain.payment;

public interface PaymentRepository {

    PaymentModel save(PaymentModel payment);

    PaymentModel getByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
