package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    /** 정합성 복구 대상: PENDING이면서 transactionKey가 연결된 결제건. */
    List<PaymentModel> findPendingWithTransactionKey();

    /** 미아 종결 후보: PENDING이면서 transactionKey가 없고, cutoff 이전에 생성된 결제건. */
    List<PaymentModel> findExpirableOrphans(ZonedDateTime cutoff);
}
