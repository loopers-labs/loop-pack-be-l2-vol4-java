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

    /**
     * by-order 복구 대상 = 동기 타임아웃/서킷 강등으로 transactionKey 를 못 받은 채 threshold 보다 오래 PENDING 인 결제.
     * key 가 없어 단건 조회가 불가하므로 이후 orderId 로 PG 에 되물어 수렴시킨다.
     */
    List<PaymentModel> findStuckPendingWithoutKey(ZonedDateTime threshold);
}
