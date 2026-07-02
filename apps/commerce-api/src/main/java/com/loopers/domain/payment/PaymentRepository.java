package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    PaymentModel save(PaymentModel payment);

    Optional<PaymentModel> findByOrderId(Long orderId);

    /** 복구 스케줄러용: 시도한(pgRequestAttempted=true) 미결 PENDING 중 복구 시도 상한 미만만. */
    List<PaymentModel> findRecoverable(int maxRecoveryAttempts);
}