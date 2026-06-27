package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findByTransactionKey(String transactionKey);

    /** 유예 시간(createdBefore)이 지나도록 PENDING 인 결제. 폴링 reconciliation 대상. (transactionKey 없는 폴백 건은 제외) */
    List<Payment> findStalePending(ZonedDateTime createdBefore);
}
