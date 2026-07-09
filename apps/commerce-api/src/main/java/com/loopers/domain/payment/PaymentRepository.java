package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    PaymentModel save(PaymentModel payment);

    Optional<PaymentModel> findByOrderId(Long orderId);

    /**
     * 복구 스케줄러용: 시도한(pgRequestAttempted=true) 미결 PENDING 중 복구 시도 상한 미만만.
     * createdBefore 이전에 생성된 건만 — 정상 콜백이 도착할 유예(grace) 시간을 준 뒤에만 폴링한다.
     */
    List<PaymentModel> findRecoverable(int maxRecoveryAttempts, ZonedDateTime createdBefore);
}