package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);

    Optional<PaymentModel> findById(Long id);

    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    /**
     * 복구 대상 = transactionKey 를 받았으나 결과가 끝내 확정되지 않은 채 threshold 보다 오래 PENDING 인 결제.
     * key 가 있어야 PG 에 조회할 수 있으므로 key 없는 건은 제외한다.
     */
    List<PaymentModel> findStuckPending(ZonedDateTime threshold);
}
