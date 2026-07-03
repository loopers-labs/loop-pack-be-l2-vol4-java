package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> find(Long id);
    Optional<PaymentModel> findByOrderId(Long orderId);
    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    /**
     * 복구 대상 결제건 — TIMEOUT_PENDING 또는 일정 시간 이상 REQUESTED 상태로 머물러 있는 결제.
     * 스케줄러가 PG GET API 로 동기화하기 위해 사용한다.
     */
    List<PaymentModel> findRecoverable(ZonedDateTime requestedBefore, int limit);
}
