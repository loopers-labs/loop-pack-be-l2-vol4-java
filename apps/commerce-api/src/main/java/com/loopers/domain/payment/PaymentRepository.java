package com.loopers.domain.payment;

import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> find(Long id);

    /** 해당 주문의 '활성' 결제(PENDING 또는 SUCCESS). 멱등 가드 — 한 주문에 활성 결제는 하나뿐. */
    Optional<Payment> findActiveByOrderId(Long orderId);
}
