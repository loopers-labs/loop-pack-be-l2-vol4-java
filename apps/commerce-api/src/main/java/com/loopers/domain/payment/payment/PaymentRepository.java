package com.loopers.domain.payment.payment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findAllByOrderIds(Collection<Long> orderIds);

    List<Payment> findRequestedPayments();

    default List<Payment> findRequestedPaymentsForUpdate(int limit) {
        return findRequestedPayments();
    }
}
