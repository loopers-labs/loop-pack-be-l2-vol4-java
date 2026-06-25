package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> find(Long id);

    Optional<Payment> findByTransactionKey(String transactionKey);

    /** 해당 주문의 '활성' 결제(PENDING 또는 SUCCESS). 멱등 가드 — 한 주문에 활성 결제는 하나뿐. */
    Optional<Payment> findActiveByOrderId(Long orderId);

    /** 기한(threshold) 이전에 생성됐는데 아직 PENDING 인 결제들 — 콜백 누락 보정(폴링) 대상. */
    List<Payment> findPendingOlderThan(ZonedDateTime threshold);
}
