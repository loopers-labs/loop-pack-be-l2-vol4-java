package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    PaymentModel save(PaymentModel payment);

    Optional<PaymentModel> findByOrderId(Long orderId);

    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    List<PaymentModel> findAllByStatus(PaymentStatus status);

    /** 거래키 없이 cutoff 이전에 생성된 PENDING 결제 (요청 타임아웃 복구 대상). */
    List<PaymentModel> findKeylessPendingBefore(ZonedDateTime cutoff);
}
